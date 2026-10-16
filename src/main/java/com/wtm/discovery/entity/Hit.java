package com.wtm.discovery.entity;

import java.util.UUID;

/** A meme and how well it matched, higher is better. */
public record Hit(UUID id, double score) {
}
