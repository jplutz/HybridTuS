# Loop Flowchart Visualization

![For Loop Flowchart](/figures/loop-flowchart.png)

## Understanding Loop Flow

This diagram shows how a `for` loop executes:

### Step-by-Step Process:

1. **Initialize**: Set loop variable (e.g., `i = 0`)
2. **Check Condition**: Is `i < 10`?
   - If **YES**: Continue to step 3
   - If **NO**: Exit loop
3. **Execute Body**: Run the code inside the loop
4. **Update**: Increment loop variable (`i++`)
5. **Repeat**: Go back to step 2

### Visual Flow:

```
┌─────────────┐
│ Initialize  │
│   i = 0     │
└──────┬──────┘
       │
       ▼
┌─────────────┐
│ Condition?  │◄─────┐
│  i < 10     │      │
└──────┬──────┘      │
       │ Yes         │
       ▼             │
┌─────────────┐      │
│ Loop Body   │      │
│ print(i)    │      │
└──────┬──────┘      │
       │             │
       ▼             │
┌─────────────┐      │
│ Update i++  │──────┘
└─────────────┘
       │ No
       ▼
┌─────────────┐
│  Continue   │
│   Program   │
└─────────────┘
```

## Key Insight

The condition is checked **before** each iteration, not after. If the condition is false at the start, the loop body never executes!

### Example:
```python
for i in range(0):  # Empty range
    print("This never prints")
```
