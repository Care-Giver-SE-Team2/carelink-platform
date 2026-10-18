// Runs only in the explicit local fixture; never imported by the application or CI/CD.
const violations = [];
const strict = document.currentScript.dataset.strict === 'true';
let phase = 'application-load';
document.addEventListener('securitypolicyviolation', event => {
  violations.push({phase, directive: event.effectiveDirective, blocked: event.blockedURI,
    source: event.sourceFile, line: event.lineNumber});
});
window.addEventListener('load', async () => {
  phase = 'fonts';
  const fonts = [];
  for (const [family, weights] of [['Instrument Sans', [400, 500, 600, 700]], ['IBM Plex Mono', [400, 500, 600]]]) {
    for (const weight of weights) {
      const faces = await document.fonts.load(`${weight} 16px "${family}"`, 'CareLink 2026');
      fonts.push({family, weight, loaded: faces.length > 0 && faces.every(face => face.status === 'loaded')});
    }
  }
  const api = await fetch('/api/auth/csrf').then(response => response.ok);
  phase = 'direct-style-properties';
  const inlineStyle = document.createElement('span');
  inlineStyle.style.width = '137px';
  inlineStyle.style.display = 'block';
  document.body.append(inlineStyle);
  const styles = getComputedStyle(inlineStyle).width === '137px';
  inlineStyle.remove();
  // Test the iframe navigation used by certificate dialogs. Native PDF rendering still needs
  // an actual uploaded certificate and a browser with its PDF viewer enabled.
  const frame = document.createElement('iframe');
  phase = 'same-origin-frame';
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
  phase = 'blob-frame';
  const blobFrame = document.createElement('iframe');
  const blobLoaded = new Promise(resolve => {
    blobFrame.onload = () => resolve(true);
    setTimeout(() => resolve(false), 3000);
  });
  const blobUrl = URL.createObjectURL(new Blob(['<!doctype html><title>Local preview</title>'], {type: 'text/html'}));
  blobFrame.src = blobUrl;
  blobFrame.hidden = true;
  document.body.append(blobFrame);
  const blobFrameAllowed = await blobLoaded;
  blobFrame.remove();
  URL.revokeObjectURL(blobUrl);
  phase = 'data-image';
  const dataImage = new Image();
  const imageLoaded = new Promise(resolve => {
    dataImage.onload = () => resolve(true);
    dataImage.onerror = () => resolve(false);
  });
  dataImage.src = 'data:image/gif;base64,R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7';
  const dataImageAllowed = await imageLoaded;
  await new Promise(resolve => setTimeout(resolve, 200));
  const productionResourcesUseSameOrigin = performance.getEntriesByType('resource')
    .filter(entry => ['script', 'link', 'css', 'img'].includes(entry.initiatorType))
    .every(entry => new URL(entry.name).origin === location.origin || entry.name.startsWith('data:'));
  const unexpected = violations.slice();
  phase = 'expected-negative-probes';
  // These two attempts must be blocked before execution/network access.
  const script = document.createElement('script');
  script.textContent = 'window.cspUnexpectedExecution = true';
  document.head.append(script);
  await fetch('https://csp-denied.invalid/probe').catch(() => {});
  const attributeTarget = document.createElement('div');
  const blockTarget = document.createElement('div');
  const blockStyle = document.createElement('style');
  const foreignImage = new Image();
  const foreignFrame = document.createElement('iframe');
  if (strict) {
    attributeTarget.hidden = blockTarget.hidden = foreignFrame.hidden = true;
    blockTarget.id = 'csp-inline-block-target';
    document.body.append(attributeTarget, blockTarget);
    attributeTarget.setAttribute('style', 'width: 991px');
    blockStyle.textContent = '#csp-inline-block-target { width: 811px; }';
    document.head.append(blockStyle);
    foreignImage.src = 'https://csp-denied.invalid/image.svg';
    foreignFrame.src = 'https://csp-denied.invalid/frame';
    document.body.append(foreignFrame);
  }
  await new Promise(resolve => setTimeout(resolve, 200));
  const negative = violations.slice(unexpected.length);
  const result = {
    route: location.pathname,
    fonts, sameOriginApi: api, directStyleProperties: styles, sameOriginFrameNavigation: frameAllowed,
    blobFrameNavigation: blobFrameAllowed, dataImage: dataImageAllowed,
    nativePdfRendering: 'not tested; requires a real certificate and PDF viewer',
    productionResourcesUseSameOrigin,
    unexpectedViolations: unexpected,
    inlineScriptBlocked: !window.cspUnexpectedExecution && negative.some(event => event.directive === 'script-src-elem'),
    foreignConnectionBlocked: negative.some(event => event.directive === 'connect-src'),
    strictCandidate: strict,
    inlineAttributeBlocked: strict && getComputedStyle(attributeTarget).width !== '991px'
      && negative.some(event => event.directive === 'style-src-attr'),
    inlineStyleBlockBlocked: strict && getComputedStyle(blockTarget).width !== '811px'
      && negative.some(event => event.directive === 'style-src-elem'),
    foreignImageBlocked: strict && negative.some(event => event.directive === 'img-src'),
    foreignFrameBlocked: strict && negative.some(event => event.directive === 'frame-src'),
  };
  attributeTarget.remove();
  blockTarget.remove();
  blockStyle.remove();
  foreignFrame.remove();
  result.passed = fonts.every(font => font.loaded) && api && styles && frameAllowed && blobFrameAllowed && dataImageAllowed
    && result.productionResourcesUseSameOrigin && unexpected.length === 0
    && result.inlineScriptBlocked && result.foreignConnectionBlocked
    && (!strict || (result.inlineAttributeBlocked && result.inlineStyleBlockBlocked
      && result.foreignImageBlocked && result.foreignFrameBlocked));
  const summary = document.createElement('p');
  summary.id = 'csp-summary';
  summary.textContent = `CSP fixture ${result.passed ? 'PASS' : 'FAIL'}; fonts ${fonts.filter(font => font.loaded).length}/7; `
    + `direct styles ${styles}; same-origin API/frame ${api && frameAllowed}; blob frame ${blobFrameAllowed}; `
    + `unexpected violations ${unexpected.length}; strict negative probes ${!strict || (result.inlineAttributeBlocked
      && result.inlineStyleBlockBlocked && result.foreignImageBlocked && result.foreignFrameBlocked)}`;
  document.getElementById('csp-result').before(summary);
  document.getElementById('csp-result').textContent = JSON.stringify(result, null, 2);
});
