import seedrandom from "seedrandom";

/**
 * Creates a deterministic random number generator for a given seed.
 * Used to make stubbed lesson data reproducible across sessions.
 */
export function createSeededRng(seed: string): seedrandom.PRNG {
    return seedrandom(seed);
}

/**
 * Returns a random integer between min (inclusive) and max (exclusive)
 * using a seeded generator instance.
 */
export function rngInt(rng: seedrandom.PRNG, min: number, max: number): number {
    return Math.floor(rng() * (max - min)) + min;
}

/**
 * Returns a random float between 0 and 1 using a seeded generator.
 */
export function rngFloat(rng: seedrandom.PRNG): number {
    return rng();
}
