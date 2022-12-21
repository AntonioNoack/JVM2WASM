package jvm.lang;

import javax.management.ObjectName;
import java.lang.management.RuntimeMXBean;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class Bean implements RuntimeMXBean {

    @Override
    public String getName() {
        return "JVM2WASM";
    }

    @Override
    public String getVmName() {
        // System.getProperty("java.vm.name")
        return getName();
    }

    @Override
    public String getVmVendor() {
        // System.getProperty("java.vm.vendor")
        return "AntonioNoack";
    }

    @Override
    public String getVmVersion() {
        // System.getProperty("java.vm.version")
        return "1.0.0";
    }

    @Override
    public String getSpecName() {
        // System.getProperty("java.vm.specification.name")
        return null;
    }

    @Override
    public String getSpecVendor() {
        return null;
    }

    @Override
    public String getSpecVersion() {
        return null;
    }

    @Override
    public String getManagementSpecVersion() {
        return null;
    }

    @Override
    public String getClassPath() {
        return "/";
    }

    @Override
    public String getLibraryPath() {
        return "/";
    }

    @Override
    public boolean isBootClassPathSupported() {
        return false;
    }

    @Override
    public String getBootClassPath() {
        return null;
    }

    @Override
    public List<String> getInputArguments() {
        return Collections.emptyList();
    }

    @Override
    public long getUptime() {
        // Returns the uptime of the Java virtual machine in milliseconds.
        return System.nanoTime() / 1_000_000;
    }

    @Override
    public long getStartTime() {
        // Returns the start time of the Java virtual machine in milliseconds. This method returns the approximate time when the Java virtual machine started.
        return System.currentTimeMillis() - getUptime();
    }

    @Override
    public Map<String, String> getSystemProperties() {
        // Returns a map of names and values of all system properties.
        // This method calls System.getProperties() to get all system properties.
        // Properties whose name or value is not a String are omitted.
        return Collections.emptyMap();
    }

    @Override
    public ObjectName getObjectName() {
        return null;
    }
}
