package sg.nus.carelink.openplatform.infrastructure.http;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Which addresses a webhook may go to: public unicast only. Literal addresses, so no DNS is asked. */
class PublicAddressesTest {

	@ParameterizedTest
	@ValueSource(strings = { "8.8.8.8", "203.116.1.1", "52.77.0.10", "2406:da18::1", "2001:4860:4860::8888" })
	void publicAddressesAreAllowed(String address) throws UnknownHostException {
		assertThat(PublicAddresses.isPublic(InetAddress.getByName(address))).isTrue();
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"127.0.0.1", "0.0.0.0", "0.1.2.3",                  // loopback, any, "this network"
			"10.0.0.1", "172.16.5.4", "172.31.255.255", "192.168.1.10", // private
			"169.254.169.254",                                  // link-local: the cloud's metadata service
			"100.64.0.1", "100.127.255.255",                    // carrier-grade NAT
			"192.0.0.8", "198.18.0.1", "198.19.255.255",        // protocol assignments, benchmarking
			"224.0.0.1", "240.0.0.1", "255.255.255.255",        // multicast, reserved, broadcast
			"::1", "::", "fe80::1", "fc00::1", "fd12:3456::1", "ff02::1", // IPv6 loopback, any, link-local, ULA, multicast
			"::ffff:127.0.0.1", "::ffff:10.0.0.1"               // IPv4-mapped
	})
	void localPrivateAndReservedAddressesAreRefused(String address) throws UnknownHostException {
		assertThat(PublicAddresses.isPublic(InetAddress.getByName(address))).isFalse();
	}

	@ParameterizedTest
	@ValueSource(strings = { "localhost", "127.0.0.1" })
	void aHostIsRefusedWhenItResolvesToALocalAddress(String host) throws UnknownHostException {
		assertThat(PublicAddresses.allPublic(host)).isFalse();
	}
}
