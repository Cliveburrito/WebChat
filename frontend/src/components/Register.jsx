import { useState } from 'react';
import './Login.css';

export default function Register({ onRegisterSuccess, onGoToLogin }) {
    const [username, setUsername] = useState('');
    const [email, setEmail] = useState('');
    const [password, setPassword] = useState('');
    const [error, setError] = useState('');
    const [isLoading, setIsLoading] = useState(false);

    const handleSubmit = async (e) => {
        e.preventDefault();
        setError('');
        setIsLoading(true);

        try {
            const response = await fetch('/api/auth/register', {
                method: 'POST',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({username, email, password})
            });

            if (!response.ok) {

                const errorData = await response.json();

                throw new Error(errorData.message || 'Κάτι πήγε στραβά στην εγγραφή.');
            }

            const data = await response.json();
            alert("Επιτυχής εγγραφή!");
            onRegisterSuccess(data);

        } catch (err) {

            setError(err.message);
        } finally {
            setIsLoading(false);
        }
    }

    return (
        <div className="login-page-wrapper">
            <div className="login-card">
                <h1>WebChat</h1>
                <p>Δημιουργήστε έναν νέο λογαριασμό</p>

                {error && <div className="error-box">⚠️ {error}</div>}

                <form onSubmit={handleSubmit}>
                    <div className="login-form-group">
                        <label>Username</label>
                        <input type="text" value={username} onChange={(e) => setUsername(e.target.value)} required />
                    </div>
                    <div className="login-form-group">
                        <label>Email</label>
                        <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} required />
                    </div>
                    <div className="login-form-group">
                        <label>Κωδικός</label>
                        <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} required />
                    </div>
                    <button type="submit" className="login-btn-primary" disabled={isLoading}>
                        {isLoading ? "Επεξεργασία..." : "Εγγραφή"}
                    </button>
                </form>
                <p className="auth-switch-text">
                    Έχετε ήδη λογαριασμό; <span onClick={onGoToLogin}>Σύνδεση εδώ</span>
                </p>
            </div>
        </div>
    );
}