package com.careercopilot.discovery;

import java.security.MessageDigest;
import java.util.Random;

public class MockEmbeddingClient implements EmbeddingClient {

    @Override
    public float[] getEmbedding(String text) {
        if (text == null) {
            text = "";
        }
        float[] vector = new float[1536];
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes("UTF-8"));

            // Generate seed from hash
            long seed = 0;
            for (int i = 0; i < Math.min(8, hash.length); i++) {
                seed = (seed << 8) | (hash[i] & 0xFF);
            }

            Random rand = new Random(seed);
            double sumSquare = 0;
            for (int i = 0; i < 1536; i++) {
                vector[i] = rand.nextFloat() * 2 - 1; // range -1.0 to 1.0
                sumSquare += vector[i] * vector[i];
            }

            // Normalize to unit length
            double magnitude = Math.sqrt(sumSquare);
            if (magnitude > 0) {
                for (int i = 0; i < 1536; i++) {
                    vector[i] = (float) (vector[i] / magnitude);
                }
            } else {
                vector[0] = 1.0f; // fallback unit vector
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate mock embedding", e);
        }
        return vector;
    }
}
