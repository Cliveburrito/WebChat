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

    const txt = await res.text();
    return txt ? JSON.parse(txt) : null;
}
