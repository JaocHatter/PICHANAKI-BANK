package com.parcial.central.services;

import java.util.ArrayList;
import java.util.List;

public class WorkerNodeInfo {
    private final String id;
    private final String address;
    private final List<String> managedPartitions = new ArrayList<>();

    public WorkerNodeInfo(String id, String address) {
        this.id = id;
        this.address = address;
    }

    public String getId() { return id; }
    public String getAddress() { return address; }
    public List<String> getManagedPartitions() { return managedPartitions; }
    public void addManagedPartition(String partitionKey) { this.managedPartitions.add(partitionKey); }
}