# Recursion - Build the Call Stack

## Active Learning Exercise

Understanding recursion requires **visualizing the call stack**. In this activity, you'll manually build the call stack for a recursive function.

## The Recursive Function

```python
def countdown(n):
    if n <= 0:
        print("Blastoff!")
        return
    print(n)
    countdown(n - 1)
    print(f"Returned from countdown({n})")

countdown(3)
```

## Your Task: Build the Call Stack

The **call stack** is a data structure that tracks function calls. When a function is called, it's pushed onto the stack. When it returns, it's popped off.

### Step 1: Identify Function Calls

List all the function calls that will be made:
1. `countdown(3)`
2. `countdown(?)`
3. `countdown(?)`
4. `countdown(?)`

### Step 2: Draw the Call Stack

Draw the stack at each step. The stack grows **downward** (newest call at the bottom):

**Initial Call:**
```
| countdown(3) |  <- Active frame
+--------------+
```

**After countdown(3) calls countdown(2):**
```
| countdown(3) |
+--------------+
| countdown(?) |  <- Active frame
+--------------+
```

Continue drawing the stack as it grows and shrinks...

### Step 3: Trace the Output

What will be printed? Write the output in order:

```
1. ?
2. ?
3. ?
4. ?
5. ?
6. ?
7. ?
```

## Solution

<details>
<summary>Click to reveal the full solution</summary>

### Call Stack Evolution:

**Step 1:** `countdown(3)` is called
```
| countdown(3) |  <- Prints "3", then calls countdown(2)
+--------------+
```

**Step 2:** `countdown(2)` is called
```
| countdown(3) |
+--------------+
| countdown(2) |  <- Prints "2", then calls countdown(1)
+--------------+
```

**Step 3:** `countdown(1)` is called
```
| countdown(3) |
+--------------+
| countdown(2) |
+--------------+
| countdown(1) |  <- Prints "1", then calls countdown(0)
+--------------+
```

**Step 4:** `countdown(0)` is called
```
| countdown(3) |
+--------------+
| countdown(2) |
+--------------+
| countdown(1) |
+--------------+
| countdown(0) |  <- Base case! Prints "Blastoff!", returns
+--------------+
```

**Step 5:** `countdown(0)` returns to `countdown(1)`
```
| countdown(3) |
+--------------+
| countdown(2) |
+--------------+
| countdown(1) |  <- Prints "Returned from countdown(1)", returns
+--------------+
```

**Step 6:** `countdown(1)` returns to `countdown(2)`
```
| countdown(3) |
+--------------+
| countdown(2) |  <- Prints "Returned from countdown(2)", returns
+--------------+
```

**Step 7:** `countdown(2)` returns to `countdown(3)`
```
| countdown(3) |  <- Prints "Returned from countdown(3)", returns
+--------------+
```

### Complete Output:
```
3
2
1
Blastoff!
Returned from countdown(1)
Returned from countdown(2)
Returned from countdown(3)
```

</details>

## Challenge Activity

Draw the call stack for this factorial function:

```javascript
function factorial(n) {
    if (n <= 1) {
        return 1;
    }
    return n * factorial(n - 1);
}

factorial(4)
```

**Hint:** Track both the stack frames AND the return values flowing back up!

## Reflection Questions

1. Why does the "Returned from..." message print in reverse order?
2. What happens to memory if we call `countdown(1000000)`? Why?
3. How is the call stack different from a loop's execution?

## Key Takeaway

By manually building call stacks, you develop:
- Visual understanding of how recursion works
- Intuition for why base cases are critical
- Awareness of memory usage (stack depth)
- Ability to debug recursive functions

Active visualization transforms recursion from mysterious to mechanical!
