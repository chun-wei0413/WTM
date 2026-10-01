package com.memehub.adapter.out.persistence;

/**
 * Formats a float array as a pgvector literal, e.g. {@code [0.1,0.2]}.
 */
final class VectorLiteral {

    private VectorLiteral() {
    }

    static String of(float[] vector) {
        StringBuilder sb = new StringBuilder(vector.length * 8).append('[');
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(vector[i]);
        }
        return sb.append(']').toString();
    }
}
