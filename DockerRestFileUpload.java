
/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 *  under the License.
 *
 * This class allows to upload files into running Docker container via docker rest API (v.18 + required)
 * 
 * Upload is implemented by emulating 
 * 
 * docker exec -i ubuntu /bin/bash -c 'cat > file' < file 
 * 
 * behavior.
 * 
 * Author: Boris Treukhov
 * 
 * boris@treukhov.com
 * 
 * Based on the HttpHijack class by Nick (missedone) Tan: https://gist.github.com/missedone/76517e618486db746056
 * and answer on StackOverlow http://stackoverflow.com/a/24167546/241986
 */

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.Scanner;

import javax.net.SocketFactory;
import javax.net.ssl.SSLSocketFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DockerRestFileUpload {
	private String filename;
	private String host;
	private String path;
	private String execId;
	private String containerId;

	private DockerRestFileUpload(String serverAddress, String containerId,
			String filename) {
		uri = URI.create(serverAddress);
		host = uri.getHost();
		path = uri.getPath();
		if (path.equals("")) {
			path = "/";
		}
		String query = uri.getQuery();
		if (query != null) {
			path = path + "?" + query;
		}
		try {
			socket = createSocket(uri);
		} catch (IOException e) {
			throw new IllegalStateException("Failed to create socket", e);
		}
		this.containerId = containerId;
		this.filename = filename;
	}

	/**
	 * Creates an output stream, which redirects its input to the file in the
	 * docker container.
	 * 
	 * @param serverAddress
	 *            Address of the server, for example http://localhost:4243
	 * @param containerId
	 *            Container Id of its leading part, for example 604abc
	 * @param fileName
	 *            The name of the file, for example /var/log/test
	 * @return
	 * @throws IOException
	 */
	public static OutputStream uploadFile(String serverAddress,
			String containerId, String fileName) throws IOException {
		final DockerRestFileUpload uploadFile = new DockerRestFileUpload(
				serverAddress, containerId, fileName);
		boolean needToClose = true;
		try {
			uploadFile.execCreate();
			uploadFile.execStartAndUpgrade();
			final OutputStream os = uploadFile.socket.getOutputStream();
			try{
				return new OutputStream() {
					@Override
					public void write(int b) throws IOException {
						os.write(b);
					}

					@Override
					public void write(byte[] b) throws IOException {
						os.write(b);
					}

					@Override
					public void write(byte[] b, int off, int len)
						throws IOException {
						os.write(b, off, len);
					}

					@Override
					public void close() throws IOException {
						os.flush();
						uploadFile.close();
					}
				};
			}
			finally{
				needToClose = false;
			}
		} finally {
			if (needToClose)
				try {
					uploadFile.close();
				} catch (IOException e) {
				}
		}

	}

	private static Socket createSocket(URI uri) throws java.io.IOException {
		String scheme = uri.getScheme();

		String host = uri.getHost();

		int port = uri.getPort();
		if (port == -1) {
			if (scheme.equals("https")) {
				port = 443;
			} else if (scheme.equals("http")) {
				port = 80;
			} else {
				throw new IllegalArgumentException("Unsupported scheme");
			}
		}

		if (scheme.equals("https")) {
			SocketFactory factory = SSLSocketFactory.getDefault();
			return factory.createSocket(host, port);
		} else {
			return new Socket(host, port);
		}
	}

	private void execCreate() throws IOException {
		///Note to docker-java users - currently it's not able to encode ">" as escape sequence in JSON
		///its either ">" or "\\u003e" instead of \u003e which causes inconvenience
		String payload = "{\"Detach\": false,\"Tty\": false,\"AttachStdin\" :true, "
				+ "\"AttachStdout\" :true,\"AttachStderr\":true,"
				+ "\"Cmd\":[\"/bin/bash\", \"-c\","
				+ " \"touch '"
				+ filename
				+ "' && echo 'ok' && false || cat \\" + "u003e '" + filename + "' \"]}";


		StringBuffer request = new StringBuffer();
		request.append("POST " + path + "containers/" + containerId
				+ "/exec" + " HTTP/1.1\r\n");
		request.append("Host: " + host + "\r\n");
		request.append("Content-Type: application/json\r\n");
		request.append("Content-Length: " + payload.length() + "\r\n");
		request.append("\r\n");
		request.append(payload);

		socket.getOutputStream().write(
				request.toString().getBytes(Charset.forName("UTF-8")));
		socket.getOutputStream().flush();

		BufferedReader reader = new BufferedReader(new InputStreamReader(
				socket.getInputStream()));
		String header = reader.readLine();

		if (header.contains("404"))
			throw new IllegalStateException("Container with ID " + containerId
					+ " not found");
		String line;
		do {
			line = reader.readLine();
			if (line != null && line.contains("\"Id\":")) {
				@SuppressWarnings("resource")
				Scanner scanner = new Scanner(line).useDelimiter(":");
				String s = scanner.next();
				if (s.contains("Id")) {
					String sid = scanner.next();
					this.execId = sid.replaceAll("[\",\\}]*", "");
				}
				break;
			}
		} while (line != null);
		if (this.execId == null)
			throw new IllegalStateException("Failed to start exec");
	}

	public static void main(String[] args) throws Exception {
		BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(
				DockerRestFileUpload.uploadFile("http://localhost:4243", "c0111a111e", 
					"/var/log/example.txt")));
		try { 
			bw.write("Mabel and Dipper!\n"); 
		} finally {
			bw.close();
		}
	}

	private void execStartAndUpgrade() throws IOException {
		String payload = "{\"Detach\": false,\"Tty\": false,\"AttachStdin\" :true, \"AttachStdout\" :true,\"AttachStderr\":true}";

		StringBuffer request = new StringBuffer();
		request.append("POST " + path + "exec/" + execId + "/start"
				+ " HTTP/1.1\r\n");
		request.append("Upgrade: tcp\r\n");
		request.append("Connection: Upgrade\r\n");
		request.append("Host: " + host + "\r\n");
		request.append("Content-Type: application/json\r\n");
		request.append("Content-Length: " + payload.length() + "\r\n");

		request.append("\r\n");
		request.append(payload);

		socket.getOutputStream().write(
				request.toString().getBytes(Charset.forName("UTF-8")));
		socket.getOutputStream().flush();

		BufferedReader reader = new BufferedReader(new InputStreamReader(
				socket.getInputStream()));
		String header = reader.readLine();
		if (!header.equals("HTTP/1.1 101 UPGRADED")) {
			throw new IOException("Invalid handshake response: " + header);
		}

		do {
			header = reader.readLine();
			log.info("header: {}", header);
		} while (!header.equals(""));
		
		String ok = reader.readLine();
		if (!ok.trim().equals("ok")) {
			throw new IllegalStateException("Unexpected result of touch. " + ok);
		}

	}

	private static final Logger log = LoggerFactory.getLogger(DockerRestFileUpload.class);

	private URI uri;

	public Socket socket;

	private DockerRestFileUpload(URI url) {
		uri = url;
	}

	public void close() throws java.io.IOException {
		socket.close();
	}

}
