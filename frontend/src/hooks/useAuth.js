import { useCallback, useMemo, useState } from "react";

export function useAuth() {
    const [token, setToken] = useState(localStorage.getItem("token"));
    const [currentUser, setCurrentUser] = useState(localStorage.getItem("username") || "");
    const [authView, setAuthView] = useState("login");

    const isAuthed = useMemo(() => Boolean(token), [token]);

    const loginSuccess = useCallback((t, u) => {
        setToken(t);
        setCurrentUser(u);
        localStorage.setItem("token", t);
        localStorage.setItem("username", u);
    }, []);

    const logout = useCallback(() => {
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
