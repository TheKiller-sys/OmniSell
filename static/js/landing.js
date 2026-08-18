// ============================================
// SCRIPTS ESPECÍFICOS PARA LA LANDING PAGE
// ============================================

(function() {
    'use strict';

    // ============================================
    // CONTADOR DE ESTADÍSTICAS (ANIMACIÓN)
    // ============================================
    function animateCounters() {
        const counters = document.querySelectorAll('.stat-item .number');
        
        counters.forEach(counter => {
            const text = counter.textContent;
            const number = parseInt(text.replace(/[^0-9]/g, ''));
            const suffix = text.replace(/[0-9]/g, '');
            
            if (isNaN(number)) return;
            
            let current = 0;
            const increment = number / 60;
            const duration = 1500;
            const stepTime = duration / 60;
            
            const timer = setInterval(() => {
                current += increment;
                if (current >= number) {
                    current = number;
                    clearInterval(timer);
                }
                counter.textContent = Math.floor(current) + suffix;
            }, stepTime);
        });
    }

    // ============================================
    // EFECTO DE TÍTULO CON MÁQUINA DE ESCRIBIR
    // ============================================
    function typeWriter(element, texts, speed = 80) {
        if (!element || !texts || texts.length === 0) return;
        
        let textIndex = 0;
        let charIndex = 0;
        let isDeleting = false;
        
        function type() {
            const currentText = texts[textIndex];
            
            if (!isDeleting) {
                element.textContent = currentText.substring(0, charIndex + 1);
                charIndex++;
                
                if (charIndex === currentText.length) {
                    isDeleting = true;
                    setTimeout(type, 2000);
                    return;
                }
            } else {
                element.textContent = currentText.substring(0, charIndex - 1);
                charIndex--;
                
                if (charIndex === 0) {
                    isDeleting = false;
                    textIndex = (textIndex + 1) % texts.length;
                    setTimeout(type, 500);
                    return;
                }
            }
            
            setTimeout(type, isDeleting ? speed / 2 : speed);
        }
        
        type();
    }

    // ============================================
    // EFECTO PARALLAX EN EL HERO
    // ============================================
    function parallaxEffect() {
        const hero = document.querySelector('.hero-section');
        if (!hero) return;
        
        window.addEventListener('scroll', () => {
            const scrolled = window.pageYOffset;
            const rate = scrolled * 0.3;
            
            hero.style.backgroundPositionY = rate + 'px';
        }, { passive: true });
    }

    // ============================================
    // MEJORA DE ACCESIBILIDAD
    // ============================================
    function improveAccessibility() {
        // Añadir aria-labels faltantes
        document.querySelectorAll('a[href^="#"]').forEach(link => {
            if (!link.getAttribute('aria-label')) {
                link.setAttribute('aria-label', `Navegar a ${link.textContent.trim()}`);
            }
        });
        
        // Asegurar que los botones tengan texto
        document.querySelectorAll('button').forEach(btn => {
            if (!btn.textContent.trim() && !btn.getAttribute('aria-label')) {
                btn.setAttribute('aria-label', 'Botón');
            }
        });
    }

    // ============================================
    // DETECCIÓN DE PREFERENCIA DE TEMA OSCURO
    // ============================================
    function detectDarkMode() {
        const prefersDark = window.matchMedia('(prefers-color-scheme: dark)');
        
        prefersDark.addEventListener('change', (e) => {
            if (e.matches) {
                document.documentElement.setAttribute('data-bs-theme', 'dark');
            } else {
                document.documentElement.setAttribute('data-bs-theme', 'light');
            }
        });
    }

    // ============================================
    // PERFORMANCE: LAZY LOAD DE IMÁGENES
    // ============================================
    function lazyLoadImages() {
        const images = document.querySelectorAll('img[data-src]');
        
        if ('IntersectionObserver' in window) {
            const observer = new IntersectionObserver((entries) => {
                entries.forEach(entry => {
                    if (entry.isIntersecting) {
                        const img = entry.target;
                        img.src = img.dataset.src;
                        img.removeAttribute('data-src');
                        observer.unobserve(img);
                    }
                });
            });
            
            images.forEach(img => observer.observe(img));
        } else {
            // Fallback para navegadores antiguos
            images.forEach(img => {
                img.src = img.dataset.src;
                img.removeAttribute('data-src');
            });
        }
    }

    // ============================================
    // ANALYTICS TRACKING (opcional)
    // ============================================
    function trackEvents() {
        // Eventos de clicks en botones CTA
        document.querySelectorAll('.btn-primary-hero, .btn-cta, .btn-primary-nav').forEach(btn => {
            btn.addEventListener('click', function() {
                // Aquí iría el código de analytics
                console.log('CTA Click:', this.textContent.trim());
                
                // Ejemplo con Google Analytics
                if (typeof gtag !== 'undefined') {
                    gtag('event', 'conversion', {
                        'send_to': 'AW-XXXXXXXXX/YYYYYYYY',
                        'value': 1.0,
                        'currency': 'USD'
                    });
                }
            });
        });
    }

    // ============================================
    // INICIALIZACIÓN
    // ============================================
    document.addEventListener('DOMContentLoaded', function() {
        // Animar contadores cuando sean visibles
        const statsSection = document.querySelector('.hero-stats');
        if (statsSection) {
            const observer = new IntersectionObserver((entries) => {
                entries.forEach(entry => {
                    if (entry.isIntersecting) {
                        animateCounters();
                        observer.unobserve(entry.target);
                    }
                });
            }, { threshold: 0.3 });
            
            observer.observe(statsSection);
        }
        
        // Efecto de máquina de escribir (opcional)
        const typingElement = document.querySelector('.typing-effect');
        if (typingElement) {
            const texts = [
                'como un profesional',
                'sin complicaciones',
                'desde cualquier lugar',
                'en tiempo real'
            ];
            typeWriter(typingElement, texts);
        }
        
        // Mejoras de accesibilidad
        improveAccessibility();
        
        // Detección de tema oscuro
        detectDarkMode();
        
        // Lazy loading
        lazyLoadImages();
        
        // Tracking de eventos
        trackEvents();
        
        // Parallax en hero
        parallaxEffect();
        
        // Log de bienvenida
        console.log('🚀 OmniVentas Landing Page cargada correctamente');
        console.log('📊 Optimizada para conversión y retención de usuarios');
    });

})();
