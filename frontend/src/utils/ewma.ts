// Exponential Weighted Moving Average (EWMA)
// Used for estimating mastery progression client-side before backend authority

export function ewma(
    previous: number,
    newValue: number,
    alpha = 0.4
): number {
    return alpha * newValue + (1 - alpha) * previous;
}

// Example usage:
// let mastery = ewma(0.6, 1); // updates mastery toward 1 with smoothing
