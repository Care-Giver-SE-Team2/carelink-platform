package sg.nus.carelink.openplatform.infrastructure.http;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Which addresses CareLink's server may send a webhook to. A provider chooses its own webhook
 * URL, so without this check it could point CareLink at the cloud's metadata service
 * (169.254.169.254), a service inside the cluster or the server itself, and have CareLink call it
 * (server-side request forgery). Only public unicast addresses are allowed.
 *
 * <p>The host is resolved here and again when the request connects, so a name that changes between
 * the two (DNS rebinding) could slip past: the cluster's egress rules are the second line.
 */
final class PublicAddresses {

	private PublicAddresses() {
	}

	/** Whether every address {@code host} resolves to is public. */
	static boolean allPublic(String host) throws UnknownHostException {
		InetAddress[] addresses = InetAddress.getAllByName(host);
		for (InetAddress address : addresses) {
			if (!isPublic(address)) {
				return false;
			}
		}
		return addresses.length > 0;
	}

	static boolean isPublic(InetAddress address) {
		if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
				|| address.isSiteLocalAddress() || address.isMulticastAddress()) {
			return false;
		}
		byte[] bytes = address.getAddress();
		if (address instanceof Inet4Address) {
			int first = bytes[0] & 0xff;
			int second = bytes[1] & 0xff;
			return first != 0                                       // 0.0.0.0/8, "this network"
					&& !(first == 100 && (second & 0xc0) == 64)     // 100.64.0.0/10, carrier-grade NAT
					&& !(first == 192 && second == 0 && (bytes[2] & 0xff) == 0) // 192.0.0.0/24, protocol assignments
					&& !(first == 198 && (second == 18 || second == 19)) // 198.18.0.0/15, benchmarking
					&& first < 240;                                 // 240.0.0.0/4 and the broadcast address
		}
		if (address instanceof Inet6Address) {
			return (bytes[0] & 0xfe) != 0xfc;                       // fc00::/7, unique local
		}
		return false;
	}
}
