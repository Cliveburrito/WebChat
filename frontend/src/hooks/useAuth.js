import { useCallback, useEffect, useMemo, useState } from "react";
import { instrumentedFetch } from "../api/apiJson";

export function useAuth() {
    const [token, setToken] = useState(localStorage.getItem("token"));
    const [currentUser, setCurrentUser] = useState(localStorage.getItem("username") || "");
    const [authView, setAuthView] = useState("login");

    const isAuthed = useMemo(() => Boolean(token), [token]);

    useEffect(() => {
        const handleTokenRefreshed = (event) => {
            const nextToken = event.detail?.token;
            const nextUsername = event.detail?.username;
            if (nextToken) setToken(nextToken);
            if (nextUsername) setCurrentUser(nextUsername);
        };
        const handleRefreshFailed = () => {
            setToken(null);
        };

        window.addEventListener("auth:token-refreshed", handleTokenRefreshed);
        window.addEventListener("auth:refresh-failed", handleRefreshFailed);
        return () => {
            window.removeEventListener("auth:token-refreshed", handleTokenRefreshed);
            window.removeEventListener("auth:refresh-failed", handleRefreshFailed);
        };
    }, []);

    const loginSuccess = useCallback((t, u) => {
        setToken(t);
        setCurrentUser(u);
        localStorage.setItem("token", t);
        localStorage.setItem("username", u);
    }, []);

    const logout = useCallback(() => {
        instrumentedFetch("/api/auth/logout", {
            method: "POST",
            credentials: "same-origin",
            skipAuthRefresh: true,
        }).catch(() => {});
        localStorage.clear();
        window.location.reload();
    }, []);

    return {
        token,
        currentUser,
        authView,
        isAuthed,
        setAuthView,
        loginSuccess,
        logout,
    };
}
