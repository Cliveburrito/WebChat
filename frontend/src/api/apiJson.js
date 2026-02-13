export async function apiJson(url, { token, method = "GET", body } = {}) {
    const res = await fetch(url, {
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
    const res = await fetch(url, {
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
