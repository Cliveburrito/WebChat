import { useState } from 'react';
import './Login.css';

export default function Login({ onLoginSuccess, onGoToRegister }) {
    const [username, setUsername] = useState('');
    const [password, setPassword] = useState('');
    const [error, setError] = useState('');
    const [isLoading, setIsLoading] = useState(false);

    const handleSubmit = async (e) => {
        e.preventDefault();
        setError('');
        setIsLoading(true);

        try {
            const response = await fetch('/api/auth/login', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ username, password })
            });

            const data = await response.json();
            if (!response.ok) throw new Error(data.message || 'Login failed.');

            const token = data.accessToken || data.token;
            onLoginSuccess(token, username);
        } catch (err) {
            setError(err.message);
        } finally {
            setIsLoading(false);
        }
    };

    return (
        <div className="login-page-wrapper">
            {}
            <ul className="bubbles">
                <li></li><li></li><li></li><li></li><li></li>
            </ul>

            {}
            <div className="login-card">
                <h1>WebChat</h1>
                <p>Συνδεθείτε για να ξεκινήσετε</p>

                {error && <div className="error-box">⚠️ {error}</div>}

                <form onSubmit={handleSubmit}>
                    <div className="login-form-group">
                        <label>Username</label>
                        <input
                            type="text"
                            value={username}
                            onChange={(e) => setUsername(e.target.value)}
                            placeholder="Το username σας"
                            required
                        />
                    </div>
                    <div className="login-form-group">
                        <label>Κωδικός</label>
                        <input
                            type="password"
                            value={password}
                            onChange={(e) => setPassword(e.target.value)}
                            placeholder="Ο κωδικός σας"
                            required
                        />
                    </div>
                    <button type="submit" className="login-btn-primary" disabled={isLoading}>
                        {isLoading ? "Σύνδεση..." : "Είσοδος"}
                    </button>
                </form>

                <p className="auth-switch-text">
                    Δεν έχετε λογαριασμό; <span onClick={onGoToRegister}>Εγγραφή</span>
                </p>
            </div>
        </div>
    );
}