package dev.shrd.launcher;

import dev.shrd.launcher.launch.Http;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;

import java.nio.charset.StandardCharsets;

/**
 * Opens the server's port on the router, so a friend can type one address and join from anywhere.
 * <p>
 * This is UPnP IGD — the same mechanism games and torrent clients use. It only works when the router
 * has UPnP enabled (most home routers do by default, some ship with it off), and it never touches
 * anything but the one TCP port it asks for, removed again when the server stops.
 * <p>
 * When the router refuses or is not there, nothing breaks: the caller keeps showing the LAN address
 * and the manual instructions, rather than pretending the server is reachable.
 */
public final class PortMapper {

	/** What the caller needs to tell the user, whatever happened. */
	public record Result(boolean mapped, String publicAddress, String message) {
	}

	private static final String SSDP_ADDRESS = "239.255.255.250";
	private static final int SSDP_PORT = 1900;

	private String controlUrl;
	private String serviceType;
	private String publicIp;
	private int mappedPort;

	/** Asks the router for a mapping, then reports the address a friend would type. */
	public Result open(int port) {
		try {
			String location = discoverGateway();

			if (location == null) {
				return new Result(false, "", "No UPnP router answered — forward TCP " + port + " yourself, or use a tunnel.");
			}

			if (!describe(location)) {
				return new Result(false, "", "The router answered but offers no port-mapping service.");
			}

			String internal = localAddress();

			if (internal == null) {
				return new Result(false, "", "Could not work out this PC's address on the network.");
			}

			addMapping(port, internal);
			mappedPort = port;
			publicIp = externalAddress();
			return new Result(true, publicAddress(),
				"Port " + port + " is open on the router, pointing at " + internal + ".");
		} catch (Exception e) {
			return new Result(false, "", "Could not open the port automatically: " + Http.describe(e));
		}
	}

	public boolean mapped() {
		return mappedPort > 0 && publicIp != null && !publicIp.isBlank();
	}

	/** The address to hand a friend: public IP and the port, nothing else. */
	public String publicAddress() {
		return mapped() ? publicIp + ":" + mappedPort : "";
	}

	public String publicIp() {
		return publicIp == null ? "" : publicIp;
	}

	/** Removes the mapping again, so stopping the server does not leave a hole in the router. */
	public void close() {
		if (controlUrl == null || mappedPort == 0) {
			return;
		}

		try {
			soap(controlUrl, serviceType, "DeletePortMapping", "<NewRemoteHost></NewRemoteHost>"
				+ "<NewExternalPort>" + mappedPort + "</NewExternalPort>"
				+ "<NewProtocol>TCP</NewProtocol>");
		} catch (Exception ignored) {
			// The mapping times out with the router's lease anyway; nothing to recover here.
		}

		mappedPort = 0;
	}

	// ------------------------------------------------------------------ discovery

	/** SSDP: shout on the multicast address and take the first gateway that answers. */
	private String discoverGateway() throws Exception {
		String request = "M-SEARCH * HTTP/1.1\r\n"
			+ "HOST: " + SSDP_ADDRESS + ":" + SSDP_PORT + "\r\n"
			+ "MAN: \"ssdp:discover\"\r\n"
			+ "MX: 2\r\n"
			+ "ST: urn:schemas-upnp-org:device:InternetGatewayDevice:1\r\n\r\n";

		byte[] payload = request.getBytes(StandardCharsets.UTF_8);

		try (DatagramSocket socket = new DatagramSocket()) {
			socket.setSoTimeout(2500);
			socket.send(new DatagramPacket(payload, payload.length,
				new InetSocketAddress(InetAddress.getByName(SSDP_ADDRESS), SSDP_PORT)));

			byte[] buffer = new byte[4096];
			long deadline = System.currentTimeMillis() + 3000;

			while (System.currentTimeMillis() < deadline) {
				DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

				try {
					socket.receive(packet);
				} catch (java.net.SocketTimeoutException timeout) {
					break;
				}

				String response = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);

				for (String line : response.split("\r?\n")) {
					if (line.toLowerCase(java.util.Locale.ROOT).startsWith("location:")) {
						return line.substring("location:".length()).trim();
					}
				}
			}
		}

		return null;
	}

	/** Reads the gateway's description and finds its WAN connection service. */
	private boolean describe(String location) throws Exception {
		Document document = parse(Http.getBytes(location));

		for (String candidate : new String[] {
			"urn:schemas-upnp-org:service:WANIPConnection:1",
			"urn:schemas-upnp-org:service:WANPPPConnection:1" }) {
			NodeList services = document.getElementsByTagName("service");

			for (int index = 0; index < services.getLength(); index++) {
				Element service = (Element) services.item(index);
				String type = text(service, "serviceType");

				if (candidate.equals(type)) {
					String control = text(service, "controlURL");

					if (!control.isBlank()) {
						serviceType = type;
						controlUrl = java.net.URI.create(location).resolve(control).toURL().toString();
						return true;
					}
				}
			}
		}

		return false;
	}

	private void addMapping(int port, String internal) throws Exception {
		soap(controlUrl, serviceType, "AddPortMapping",
			"<NewRemoteHost></NewRemoteHost>"
				+ "<NewExternalPort>" + port + "</NewExternalPort>"
				+ "<NewProtocol>TCP</NewProtocol>"
				+ "<NewInternalPort>" + port + "</NewInternalPort>"
				+ "<NewInternalClient>" + internal + "</NewInternalClient>"
				+ "<NewEnabled>1</NewEnabled>"
				+ "<NewPortMappingDescription>Shrd Launcher</NewPortMappingDescription>"
				+ "<NewLeaseDuration>0</NewLeaseDuration>");
	}

	/** Ask the router for its own WAN address; falls back to a public echo service. */
	private String externalAddress() {
		try {
			String body = soap(controlUrl, serviceType, "GetExternalIPAddress", "");
			String found = extract(body, "NewExternalIPAddress");

			if (!found.isBlank()) {
				return found;
			}
		} catch (Exception ignored) {
			// Falls through to the echo service.
		}

		try {
			return Http.getString("https://api.ipify.org").trim();
		} catch (Exception ignored) {
			return "";
		}
	}

	private String localAddress() {
		String address = ServerManager.localIp();
		return address.startsWith("your-") ? null : address;
	}

	// ------------------------------------------------------------------ soap

	private String soap(String url, String type, String action, String body) throws Exception {
		String envelope = "<?xml version=\"1.0\"?>"
			+ "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" "
			+ "s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">"
			+ "<s:Body><u:" + action + " xmlns:u=\"" + type + "\">" + body + "</u:" + action + "></s:Body>"
			+ "</s:Envelope>";

		// The action header needs the service type *with* its version, exactly as advertised.
		Http.Reply reply = Http.postXml(url, envelope, type + "#" + action);

		if (!reply.ok() && reply.status() != 500) {
			throw new IllegalStateException("HTTP " + reply.status() + " from the router");
		}

		// A SOAP fault comes back as 500 with a UPnPError inside: 718 means somebody else already owns
		// the port, 724 the same, which for a server the user just started is worth naming.
		if (reply.status() == 500) {
			String code = extract(reply.body(), "errorCode");

			if ("718".equals(code) || "724".equals(code)) {
				throw new IllegalStateException("the router says port 25565 is already forwarded to another device");
			}

			String detail = code.isBlank()
				? reply.body().replaceAll("\\s+", " ").trim()
				: "UPnP " + code;

			if (detail.length() > 160) {
				detail = detail.substring(0, 160);
			}

			throw new IllegalStateException("the router refused (" + detail + ")");
		}

		return reply.body();
	}

	private static String extract(String xml, String tag) {
		int start = xml.indexOf("<" + tag + ">");

		if (start < 0) {
			return "";
		}

		int end = xml.indexOf("</" + tag + ">", start);

		if (end < 0) {
			return "";
		}

		return xml.substring(start + tag.length() + 2, end).trim();
	}

	private static Document parse(byte[] xml) throws Exception {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
		factory.setExpandEntityReferences(false);
		return factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
	}

	private static String text(Element parent, String tag) {
		NodeList nodes = parent.getElementsByTagName(tag);

		if (nodes.getLength() == 0) {
			return "";
		}

		Node node = nodes.item(0);
		return node.getTextContent() == null ? "" : node.getTextContent().trim();
	}

}
