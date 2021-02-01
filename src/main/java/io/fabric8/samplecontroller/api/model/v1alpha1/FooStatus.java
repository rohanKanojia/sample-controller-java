package io.fabric8.samplecontroller.api.model.v1alpha1;

public class FooStatus {
    private int availableReplicas;

    public int getAvailableReplicas() {
        return availableReplicas;
    }

    public void setAvailableReplicas(int availableReplicas) {
        this.availableReplicas = availableReplicas;
    }

    @Override
    public String toString() {
        return "FooStatus{ availableReplicas=" + availableReplicas + "}";
    }
}
