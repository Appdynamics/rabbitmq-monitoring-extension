package com.appdynamics.extensions.rabbitmq.conf;

public class InstanceInfo {
	
	private String host;
	private Integer port;
	private Boolean useSSL;
	private String username;
	private String password;
	private Long connectTimeout;
	private Long socketTimeout;
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
	public Long getConnectTimeout() {
		return connectTimeout;
	}
	public void setConnectTimeout(Long connectTimeout) {
		this.connectTimeout = connectTimeout;
	}
	public Long getSocketTimeout() {
		return socketTimeout;
	}
	public void setSocketTimeout(Long socketTimeout) {
		this.socketTimeout = socketTimeout;
	}

}
