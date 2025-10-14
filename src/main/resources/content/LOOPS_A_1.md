# Loops - Trace Execution Activity

## Active Learning Exercise

This hands-on activity will help you understand how loops execute by **tracing the execution** step-by-step.

## Activity: Trace the Loop

Consider this code snippet:

```javascript
let sum = 0;
for (let i = 1; i <= 4; i++) {
    sum += i * 2;
    console.log(`i=${i}, sum=${sum}`);
}
console.log(`Final sum: ${sum}`);
```

## Your Task

**Fill in the table below** by tracing each iteration of the loop:

| Iteration | i Value | Expression (i * 2) | sum Value (before) | sum Value (after) | Output |
|-----------|---------|--------------------|--------------------|-------------------|---------|
| 1         | ?       | ?                  | 0                  | ?                 | ?       |
| 2         | ?       | ?                  | ?                  | ?                 | ?       |
| 3         | ?       | ?                  | ?                  | ?                 | ?       |
| 4         | ?       | ?                  | ?                  | ?                 | ?       |

**Final Output:** `Final sum: ?`

## Solution

<details>
<summary>Click to reveal the answer</summary>

| Iteration | i Value | Expression (i * 2) | sum Value (before) | sum Value (after) | Output |
|-----------|---------|--------------------|--------------------|-------------------|---------|
| 1         | 1       | 2                  | 0                  | 2                 | i=1, sum=2 |
| 2         | 2       | 4                  | 2                  | 6                 | i=2, sum=6 |
| 3         | 3       | 6                  | 6                  | 12                | i=3, sum=12 |
| 4         | 4       | 8                  | 12                 | 20                | i=4, sum=20 |

**Final Output:** `Final sum: 20`

</details>

## Challenge Activity

Now trace this **while loop**:

```python
count = 0
total = 1
while count < 3:
    total *= 2
    count += 1
    print(f"count={count}, total={total}")
```

Create your own trace table and verify your answer by running the code!

## Key Takeaway

By manually tracing loops, you develop an intuition for:
- How loop counters change
- When loop conditions are evaluated
- How variables are updated during each iteration
- The final state after the loop completes

This active practice builds deep understanding that passive reading cannot achieve!
