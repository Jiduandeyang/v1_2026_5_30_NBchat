const API_TIMEOUT_MS = 15000;
const API_RETRY_DELAY_MS = 500;

function sleep(ms) {
    return new Promise(resolve => setTimeout(resolve, ms));
}

async function fetchWithTimeout(url, options) {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), API_TIMEOUT_MS);
    try {
        return await fetch(url, {...options, signal: controller.signal});
    } catch (error) {
        throw new Error("网络连接不稳定，请稍后重试");
    } finally {
        clearTimeout(timer);
    }
}

window.ChatApi = {
    async request(path, options = {}) {
        const headers = options.body instanceof FormData ? {} : {"Content-Type": "application/json"};
        const method = (options.method || "GET").toUpperCase();
        const requestOptions = {
            credentials: "include",
            ...options,
            headers: {...headers, ...(options.headers || {})}
        };
        let response;
        try {
            response = await fetchWithTimeout(`api${path}`, requestOptions);
        } catch (error) {
            if (method === "GET") {
                await sleep(API_RETRY_DELAY_MS);
                response = await fetchWithTimeout(`api${path}`, requestOptions);
            } else {
                throw error;
            }
        }
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
