package io.fabric8.samplecontroller.api.model.v1alpha1;

public class FooSpec {
    private String deploymentName;
    private int replicas;

    public int getReplicas() {
        return replicas;
    }

    @Override
    public String toString() {
        return "FooSpec{replicas=" + replicas + ",deploymentName=" + deploymentName + "}";
    }

    public void setReplicas(int replicas) {
        this.replicas = replicas;
    }

    public String getDeploymentName() { return deploymentName; }

    public void setDeploymentName(String deploymentName) { this.deploymentName = deploymentName; }
}
