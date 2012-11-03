package com.benohead.tools.mysql;

public class ServerEntry {

    private static final String DEFAULT_PORT = "3306";
    private String name;
    private String value;

    public ServerEntry(String key, String value) {
        this.name = key;
        this.value = value;
    }

    public String getServerAddress() {
        return value.split(":")[0];
    }

    public String getServerName() {
        return name;
    }

    public String getServerPort() {
        String[] split = value.split(":");
        if (split.length > 1) {
            return split[1];
        }
        return DEFAULT_PORT;
    }

    public String getValue() {
        return value;
    }

    public void setServerAddress(String address) {
        String[] split = value.split(":");
        StringBuilder builder = new StringBuilder(address);
        for (int i = 1; i < split.length; i++) {
            builder.append(':').append(split[i]);
        }
        value = builder.toString();
    }

    public void setServerName(String name) {
        this.name = name;
    }

    public void setServerPort(String port) {
        String[] split = value.split(":");
        String address = "";
        if (split.length > 0) {
            address = split[0];
        }
        StringBuilder builder = new StringBuilder(address);
        builder.append(':').append(port);
        for (int i = 2; i < split.length; i++) {
            builder.append(':').append(split[i]);
        }
        value = builder.toString();
    }

    @Override
    public String toString() {
        return name;
    }
}
