/**
 * Lightweight Zero-Dependency Confetti & Sparkle Cannon for Fun Micro-Interactions
 */

export function fireConfetti(x = window.innerWidth / 2, y = window.innerHeight / 3): void {
  if (typeof window === 'undefined') return;

  const canvas = document.createElement('canvas');
  canvas.style.position = 'fixed';
  canvas.style.inset = '0';
  canvas.style.width = '100vw';
  canvas.style.height = '100vh';
  canvas.style.pointerEvents = 'none';
  canvas.style.zIndex = '99999';
  document.body.appendChild(canvas);

  const ctx = canvas.getContext('2d');
  if (!ctx) {
    canvas.remove();
    return;
  }

  const dpr = window.devicePixelRatio || 1;
  canvas.width = window.innerWidth * dpr;
  canvas.height = window.innerHeight * dpr;
  ctx.scale(dpr, dpr);

  const colors = ['#06B6D4', '#22D3EE', '#10B981', '#34D399', '#F59E0B', '#FBBF24', '#A855F7', '#EC4899'];
  const particleCount = 45;
  const particles: Array<{
    x: number;
    y: number;
    vx: number;
    vy: number;
    size: number;
    color: string;
    rotation: number;
    rotSpeed: number;
    alpha: number;
    decay: number;
  }> = [];

  for (let i = 0; i < particleCount; i++) {
    const angle = Math.random() * Math.PI * 2;
    const speed = 4 + Math.random() * 8;
    particles.push({
      x,
      y,
      vx: Math.cos(angle) * speed,
      vy: Math.sin(angle) * speed - 3,
      size: 5 + Math.random() * 6,
      color: colors[Math.floor(Math.random() * colors.length)],
      rotation: Math.random() * 360,
      rotSpeed: (Math.random() - 0.5) * 12,
      alpha: 1,
      decay: 0.015 + Math.random() * 0.02
    });
  }

  let animationFrameId: number;

  function render() {
    ctx?.clearRect(0, 0, window.innerWidth, window.innerHeight);
    let active = 0;

    for (const p of particles) {
      if (p.alpha <= 0) continue;
      active++;

      p.x += p.vx;
      p.y += p.vy;
      p.vy += 0.22; // Gravity
      p.vx *= 0.98; // Drag
      p.rotation += p.rotSpeed;
      p.alpha -= p.decay;

      if (!ctx) continue;
      ctx.save();
      ctx.globalAlpha = Math.max(0, p.alpha);
      ctx.translate(p.x, p.y);
      ctx.rotate((p.rotation * Math.PI) / 180);
      ctx.fillStyle = p.color;
      ctx.fillRect(-p.size / 2, -p.size / 2, p.size, p.size * 0.6);
      ctx.restore();
    }

    if (active > 0) {
      animationFrameId = requestAnimationFrame(render);
    } else {
      cancelAnimationFrame(animationFrameId);
      canvas.remove();
    }
  }

  animationFrameId = requestAnimationFrame(render);
}
