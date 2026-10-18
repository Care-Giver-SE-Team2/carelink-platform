"""ZAP baseline hook: the initial seed GET must not follow HTTP redirects.

The traditional spider checks its context on discovered links and redirects, but
baseline's initial zap.urlopen() uses requests.get(), which follows redirects.
Use the core API with redirects disabled before the context-bound spider starts.
"""


def zap_started(zap, target):
    # Context matching strips query strings; the spider exclusion checks raw URIs.
    if zap.spider.exclude_from_scan(r".*[?%#].*") != "OK":
        raise ValueError("Could not exclude query strings and encoded paths from the spider")
    return zap, target


def zap_access_target(zap, target):
    def open_seed(url, *args, **kwargs):
        if url != target or args or kwargs:
            raise ValueError("Only the fixed baseline seed GET is allowed")
        messages = zap.core.access_url(url, followredirects="false")
        if not isinstance(messages, list) or len(messages) != 1:
            raise ValueError("Initial baseline GET did not return one HTTP response")
        status = messages[0].get("responseHeader", "").split("\r\n", 1)[0].split()
        if len(status) < 2 or status[1] != "200":
            raise ValueError("Initial baseline GET must return 200 without redirects")
        return messages[0].get("responseBody", "")

    zap.urlopen = open_seed
    return zap, target
