window.ChatApi = {
    async request(path, options = {}) {
        const headers = options.body instanceof FormData ? {} : {"Content-Type": "application/json"};
        const response = await fetch(`api${path}`, {
            credentials: "include",
            ...options,
            headers: {...headers, ...(options.headers || {})}
        });
        if (response.headers.get("content-type")?.includes("text/html")) {
            return response;
        }
        const payload = await response.json().catch(() => ({ok: false, message: "Invalid server response"}));
        if (!response.ok || payload.ok === false) {
            throw new Error(payload.message || response.statusText);
        }
        return payload.data;
    },
    get(path) {
        return this.request(path);
    },
    post(path, body) {
        return this.request(path, {method: "POST", body: JSON.stringify(body || {})});
    },
    put(path, body) {
        return this.request(path, {method: "PUT", body: JSON.stringify(body || {})});
    },
    delete(path) {
        return this.request(path, {method: "DELETE"});
    }
};
