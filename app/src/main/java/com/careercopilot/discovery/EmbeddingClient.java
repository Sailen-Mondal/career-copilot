package com.careercopilot.discovery;

public interface EmbeddingClient {
    float[] getEmbedding(String text);
}
