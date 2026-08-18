// ============================================
// AUTH.JS - Validación y efectos para Login/Signup
// ============================================

(function() {
    'use strict';

    // ============================================
    // VALIDACIÓN EN TIEMPO REAL (SIGNUP)
    // ============================================
    function initSignupValidation() {
        const form = document.getElementById('signup-form');
        if (!form) return;

        const passwordInput = document.getElementById('password');
        const confirmInput = document.getElementById('confirm-password');
        const strengthBar = document.getElementById('strength-bar');
        const strengthText = document.getElementById('strength-text');

        // Validación de campos en tiempo real
        form.querySelectorAll('input[required]').forEach(input => {
            input.addEventListener('input', function() {
                validateField(this);
            });

            input.addEventListener('blur', function() {
                if (this.value.trim()) {
                    this.classList.remove('is-invalid');
                    this.classList.add('is-valid');
                } else {
                    this.classList.remove('is-valid');
                    this.classList.add('is-invalid');
                }
            });
        });

        // Fortaleza de contraseña
        if (passwordInput) {
            passwordInput.addEventListener('input', function() {
                const password = this.value;
                let strength = 0;
                let message = '';
                let color = '#E2E8F0';

                if (password.length === 0) {
                    strengthBar.style.width = '0%';
                    strengthText.textContent = '';
                    return;
                }

                if (password.length >= 8) strength++;
                if (password.match(/[a-z]/)) strength++;
                if (password.match(/[A-Z]/)) strength++;
                if (password.match(/[0-9]/)) strength++;
                if (password.match(/[^a-zA-Z0-9]/)) strength++;

                switch(strength) {
                    case 0:
                    case 1:
                        strengthBar.style.width = '20%';
                        color = '#EF4444';
                        message = 'Débil';
                        break;
                    case 2:
                        strengthBar.style.width = '40%';
                        color = '#F59E0B';
                        message = 'Regular';
                        break;
                    case 3:
                        strengthBar.style.width = '60%';
                        color = '#3B82F6';
                        message = 'Buena';
                        break;
                    case 4:
                        strengthBar.style.width = '80%';
                        color = '#10B981';
                        message = 'Fuerte';
                        break;
                    case 5:
                        strengthBar.style.width = '100%';
                        color = '#059669';
                        message = 'Muy Fuerte ⭐';
                        break;
                }

                strengthBar.style.background = color;
                strengthText.textContent = `Fortaleza: ${message}`;
                strengthText.style.color = color;

                // Validar longitud mínima
                if (password.length > 0 && password.length < 8) {
                    this.classList.add('is-invalid');
                    this.classList.remove('is-valid');
                } else {
                    this.classList.remove('is-invalid');
                    this.classList.add('is-valid');
                }
            });
        }

        // Confirmar contraseña
        if (confirmInput && passwordInput) {
            confirmInput.addEventListener('input', function() {
                if (this.value.length === 0) {
                    this.classList.remove('is-valid', 'is-invalid');
                    return;
                }

                if (this.value === passwordInput.value) {
                    this.classList.remove('is-invalid');
                    this.classList.add('is-valid');
                } else {
                    this.classList.remove('is-valid');
                    this.classList.add('is-invalid');
                }
            });
        }
    }

    function validateField(input) {
        if (input.value.trim()) {
            input.classList.remove('is-invalid');
            input.classList.add('is-valid');
        } else {
            input.classList.remove('is-valid');
            input.classList.add('is-invalid');
        }
    }

    // ============================================
    // VALIDACIÓN DE LOGIN
    // ============================================
    function initLoginValidation() {
        const form = document.getElementById('login-form');
        if (!form) return;

        form.querySelectorAll('input[required]').forEach(input => {
            input.addEventListener('input', function() {
                validateField(this);
            });

            input.addEventListener('blur', function() {
                if (this.value.trim()) {
                    this.classList.remove('is-invalid');
                    this.classList.add('is-valid');
                } else {
                    this.classList.remove('is-valid');
                    this.classList.add('is-invalid');
                }
            });
        });
    }

    // ============================================
    // SHOW/HIDE PASSWORD
    // ============================================
    function initTogglePassword() {
        document.querySelectorAll('.toggle-password').forEach(btn => {
            btn.addEventListener('click', function() {
                const input = document.querySelector(this.dataset.target);
                if (!input) return;

                const icon = this.querySelector('i');
                if (input.type === 'password') {
                    input.type = 'text';
                    icon.classList.remove('fa-eye');
                    icon.classList.add('fa-eye-slash');
                } else {
                    input.type = 'password';
                    icon.classList.remove('fa-eye-slash');
                    icon.classList.add('fa-eye');
                }
            });
        });
    }

    // ============================================
    // AUTO-CERRAR ALERTS
    // ============================================
    function initAutoCloseAlerts() {
        document.querySelectorAll('.auth-alert').forEach(alert => {
            setTimeout(() => {
                alert.style.opacity = '0';
                alert.style.transform = 'translateY(-10px)';
                setTimeout(() => {
                    alert.remove();
                }, 300);
            }, 5000);
        });
    }

    // ============================================
    // EFECTO DE CARGA EN BOTONES
    // ============================================
    function initLoadingButtons() {
        document.querySelectorAll('form[data-loading]').forEach(form => {
            form.addEventListener('submit', function() {
                const btn = this.querySelector('button[type="submit"]');
                if (btn) {
                    btn.classList.add('loading');
                    btn.disabled = true;
                }
            });
        });
    }

    // ============================================
    // INICIALIZACIÓN
    // ============================================
    document.addEventListener('DOMContentLoaded', function() {
        initSignupValidation();
        initLoginValidation();
        initTogglePassword();
        initAutoCloseAlerts();
        initLoadingButtons();

        console.log('🔐 Auth page loaded successfully');
    });

})();
