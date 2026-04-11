const REQUEST_ID_HEADER = "X-Request-ID";

function createRequestId() {
    if (globalThis.crypto?.randomUUID) {
        return globalThis.crypto.randomUUID();
    }
    return `req-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;
}

export async function instrumentedFetch(url, options = {}) {
    const method = options.method || "GET";
    const skipAuthRefresh = Boolean(options.skipAuthRefresh);
    const start = performance.now();
    const headers = new Headers(options.headers || {});
    const requestId = headers.get(REQUEST_ID_HEADER) || createRequestId();
    const accessTokenPresent = headers.has("Authorization");

    headers.set(REQUEST_ID_HEADER, requestId);

    console.debug("api_request_start", {
        requestId,
        method,
        url,
        accessTokenPresent,
    });

    try {
        const response = await fetch(url, {
            ...options,
            headers,
            credentials: options.credentials ?? "same-origin",
        });
        const responseRequestId = response.headers.get(REQUEST_ID_HEADER) || requestId;
        const durationMs = Math.round(performance.now() - start);

        const logPayload = {
            requestId: responseRequestId,
            method,
            url,
            status: response.status,
            durationMs,
        };

        if (response.ok) {
            console.debug("api_request_complete", logPayload);
        } else {
            console.warn("api_request_failed", logPayload);
            if (response.status === 401) {
                console.warn("auth_401", {
                    requestId: responseRequestId,
                    method,
                    url,
                    accessTokenPresent,
                    refreshConfigured: true,
                });

                if (!skipAuthRefresh && !String(url).includes("/api/auth/refresh")) {
                    const refreshed = await refreshAccessToken(responseRequestId);
                    if (refreshed?.token) {
                        console.debug("auth_original_request_retry", {
                            requestId: responseRequestId,
                            method,
                            url,
                        });

                        const retryHeaders = new Headers(headers);
                        retryHeaders.set("Authorization", `Bearer ${refreshed.token}`);
                        retryHeaders.set(REQUEST_ID_HEADER, createRequestId());

                        return instrumentedFetch(url, {
                            ...options,
                            headers: retryHeaders,
                            skipAuthRefresh: true,
                        });
                    }
                }
            }
        }

        return response;
    } catch (error) {
        console.warn("api_request_error", {
            requestId,
            method,
            url,
            durationMs: Math.round(performance.now() - start),
            error: error.name || "Error",
        });
        throw error;
    }
}

async function refreshAccessToken(parentRequestId) {
    const requestId = createRequestId();
    const start = performance.now();
    console.debug("auth_refresh_start", { requestId, parentRequestId, refreshCookieExpected: true });

    try {
        const response = await fetch("/api/auth/refresh", {
            method: "POST",
            headers: {
                "Content-Type": "application/json",
                [REQUEST_ID_HEADER]: requestId,
            },
            credentials: "same-origin",
        });

        const durationMs = Math.round(performance.now() - start);
        if (!response.ok) {
            console.warn("auth_refresh_failed", {
                requestId,
                parentRequestId,
                status: response.status,
                durationMs,
            });
            localStorage.removeItem("token");
            window.dispatchEvent(new CustomEvent("auth:refresh-failed"));
            return null;
        }

        const data = await response.json();
        const nextToken = data.accessToken || data.token;
        if (!nextToken) {
            console.warn("auth_refresh_failed", { requestId, parentRequestId, reason: "missing_token_fields", durationMs });
            return null;
        }

        localStorage.setItem("token", nextToken);
        console.debug("auth_refresh_succeeded", { requestId, parentRequestId, durationMs });
        window.dispatchEvent(new CustomEvent("auth:token-refreshed", {
            detail: {
                token: nextToken,
                username: data.user?.username,
            },
        }));
        return { token: nextToken };
    } catch (error) {
        console.warn("auth_refresh_error", {
            requestId,
            parentRequestId,
            durationMs: Math.round(performance.now() - start),
            error: error.name || "Error",
        });
        return null;
    }
}

export async function apiJson(url, { token, method = "GET", body } = {}) {
    const res = await instrumentedFetch(url, {
        method,
        headers: {
            ...(body ? { "Content-Type": "application/json" } : {}),
            ...(token ? { Authorization: `Bearer ${token}` } : {}),
        },
        ...(body ? { body: JSON.stringify(body) } : {}),
    });

    if (!res.ok) {
        const text = await res.text().catch(() => "");
        throw new Error(text || `${method} ${url} failed (${res.status})`);
    }

    const text = await res.text().catch(() => "");
    if (!text) return null;

    try {
        return JSON.parse(text);
    } catch {
        return text; // fallback
    }
}

export async function apiForm(url, { token, method = "POST", formData } = {}) {
    const res = await instrumentedFetch(url, {
        method,
        headers: {
            ...(token ? { Authorization: `Bearer ${token}` } : {}),
        },
        body: formData,
    });

    if (!res.ok) {
        const text = await res.text().catch(() => "");
        throw new Error(text || `${method} ${url} failed (${res.status})`);
    }

    const text = await res.text().catch(() => "");
    if (!text) return null;

    try {
        return JSON.parse(text);
    } catch {
        return text;
    }
}
