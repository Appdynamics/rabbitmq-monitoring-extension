package com.appdynamics.extensions.rabbitmq.conf;

public class InstanceInfo {

	private String host;
	private Integer port;
	private Boolean useSSL;
	private String username;
	private String password;
	private Integer connectTimeout;
	private Integer socketTimeout;
	private String displayName;
	private String encryptedPassword;
	private String encryptionKey;
	public String getHost() {
		return host;
	}
	public void setHost(String host) {
		this.host = host;
	}
	public Integer getPort() {
		return port;
	}
	public void setPort(Integer port) {
		this.port = port;
	}
	public Boolean getUseSSL() {
		return useSSL;
	}
	public void setUseSSL(Boolean useSSL) {
		this.useSSL = useSSL;
	}
	public String getUsername() {
		return username;
	}
	public void setUsername(String username) {
		this.username = username;
	}
	public String getPassword() {
		return password;
	}
	public void setPassword(String password) {
		this.password = password;
	}
	public Integer getConnectTimeout() {
		return connectTimeout;
	}
	public void setConnectTimeout(Integer connectTimeout) {
		this.connectTimeout = connectTimeout;
	}
	public Integer getSocketTimeout() {
		return socketTimeout;
	}
	public void setSocketTimeout(Integer socketTimeout) {
		this.socketTimeout = socketTimeout;
	}
	public String getEncryptedPassword() {
		return encryptedPassword;
	}
	public void setEncryptedPassword(String encryptedPassword) {
		this.encryptedPassword = encryptedPassword;
	}
	public String getEncryptionKey() {
		return encryptionKey;
	}
	public void setEncryptionKey(String encryptionKey) {
		this.encryptionKey = encryptionKey;
	}

	@Override
	public String toString(){
		StringBuilder builder = new StringBuilder();
		builder.append("host : " + host);
		builder.append("|");
		builder.append(" port : " + port);
		builder.append("|");
		builder.append(" useSSL : " + useSSL.toString());
		builder.append("|");
		builder.append(" username : " + username);
		builder.append("|");
		builder.append(" connectTimeout : " + connectTimeout.toString());
		builder.append("|");
		builder.append(" socketTimeout : " + socketTimeout.toString());
		builder.append("|");
		return builder.toString();
	}
	public String getDisplayName() {
		return displayName;
	}
	public void setDisplayName(String displayName) {
		this.displayName = displayName;
	}


}
