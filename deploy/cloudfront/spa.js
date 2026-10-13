function handler(event) {
    var request = event.request;
    var uri = request.uri;
    if (uri === '/api' || uri.indexOf('/api/') === 0) return request;
    if (uri === '/internal' || uri.indexOf('/internal/') === 0 ||
        uri === '/actuator' || uri.indexOf('/actuator/') === 0 || uri.indexOf('/.') !== -1) {
        return {statusCode: 404, statusDescription: 'Not Found'};
    }
    // React routes get the SPA shell; missing assets keep their real error status.
    if (uri.indexOf('.') === -1) request.uri = '/index.html';
    return request;
}
