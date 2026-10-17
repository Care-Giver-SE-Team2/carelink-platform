// Runs only in the explicit local fixture; never imported by the application or CI/CD.
const violations = [];
document.addEventListener('securitypolicyviolation', event => {
  violations.push({directive: event.effectiveDirective, blocked: event.blockedURI});
});
window.addEventListener('load', async () => {
  const fonts = [];
  for (const [family, weights] of [['Instrument Sans', [400, 500, 600, 700]], ['IBM Plex Mono', [400, 500, 600]]]) {
    for (const weight of weights) {
      const faces = await document.fonts.load(`${weight} 16px "${family}"`, 'CareLink 2026');
      fonts.push({family, weight, loaded: faces.length > 0 && faces.every(face => face.status === 'loaded')});
    }
  }
  const api = await fetch('/api/auth/csrf').then(response => response.ok);
  const inlineStyle = document.createElement('span');
  inlineStyle.style.width = '137px';
  inlineStyle.style.display = 'block';
  document.body.append(inlineStyle);
  const styles = getComputedStyle(inlineStyle).width === '137px';
  inlineStyle.remove();
  // Test the iframe navigation used by certificate dialogs. Native PDF rendering still needs
  // an actual uploaded certificate and a browser with its PDF viewer enabled.
  const frame = document.createElement('iframe');
  const frameLoaded = new Promise(resolve => {
    frame.onload = () => resolve(true);
    setTimeout(() => resolve(false), 3000);
  });
  frame.src = '/api/__csp-preview';
  frame.title = 'Local certificate navigation probe';
  frame.hidden = true;
  document.body.append(frame);
  const frameAllowed = await frameLoaded;
  frame.remove();
  await new Promise(resolve => setTimeout(resolve, 200));
  const unexpected = violations.slice();
  // These two attempts must be blocked before execution/network access.
  const script = document.createElement('script');
  script.textContent = 'window.cspUnexpectedExecution = true';
  document.head.append(script);
  await fetch('https://csp-denied.invalid/probe').catch(() => {});
  await new Promise(resolve => setTimeout(resolve, 200));
  const negative = violations.slice(unexpected.length);
  const result = {
    route: location.pathname,
    fonts, sameOriginApi: api, inlineStyles: styles, sameOriginFrameNavigation: frameAllowed,
    nativePdfRendering: 'not tested; requires a real certificate and PDF viewer',
    productionResourcesUseSameOrigin: performance.getEntriesByType('resource')
      .filter(entry => ['script', 'link', 'css', 'img'].includes(entry.initiatorType))
      .every(entry => new URL(entry.name).origin === location.origin),
    unexpectedViolations: unexpected,
    inlineScriptBlocked: !window.cspUnexpectedExecution && negative.some(event => event.directive === 'script-src-elem'),
    foreignConnectionBlocked: negative.some(event => event.directive === 'connect-src'),
  };
  result.passed = fonts.every(font => font.loaded) && api && styles && frameAllowed
    && result.productionResourcesUseSameOrigin && unexpected.length === 0
    && result.inlineScriptBlocked && result.foreignConnectionBlocked;
  document.getElementById('csp-result').textContent = JSON.stringify(result, null, 2);
});
