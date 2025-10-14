# Function Call Stack Visualization

![Call Stack Diagram](/figures/call-stack.png)

## Understanding the Call Stack

When functions call other functions, the computer uses a **call stack** to keep track of execution. Think of it like a stack of plates - you can only add or remove from the top!

### How It Works:

```python
def greet(name):
    message = make_greeting(name)
    return message

def make_greeting(name):
    return f"Hello, {name}!"

result = greet("Alice")
```

### Call Stack Evolution:

```
Step 1: Call greet("Alice")
┌──────────────────┐
│ greet("Alice")   │ ← Current frame
│ name = "Alice"   │
└──────────────────┘
│ main()           │
└──────────────────┘

Step 2: greet calls make_greeting
┌──────────────────────┐
│ make_greeting("Alice")│ ← Current frame
│ name = "Alice"        │
└──────────────────────┘
│ greet("Alice")        │
│ name = "Alice"        │
└──────────────────────┘
│ main()                │
└──────────────────────┘

Step 3: make_greeting returns "Hello, Alice!"
┌──────────────────┐
│ greet("Alice")   │ ← Current frame
│ name = "Alice"   │
│ message = "Hello, Alice!"
└──────────────────┘
│ main()           │
└──────────────────┘

Step 4: greet returns message
┌──────────────────┐
│ main()           │ ← Current frame
│ result = "Hello, Alice!"
└──────────────────┘
```

## Complex Example: Nested Calculations

```python
def calculate_final_price(base_price):
    with_tax = add_tax(base_price)
    with_discount = apply_discount(with_tax)
    return with_discount

def add_tax(price):
    tax_rate = 0.08
    return price * (1 + tax_rate)

def apply_discount(price):
    discount = 10
    return price - discount

final = calculate_final_price(100)
```

### Call Stack Trace:

```
┌─────────────────────────┐
│ apply_discount(108)     │ ← Step 3
│ price = 108             │
│ discount = 10           │
│ return: 98              │
└─────────────────────────┘
│ add_tax(100)            │ ← Step 2
│ price = 100             │
│ tax_rate = 0.08         │
│ return: 108             │
└─────────────────────────┘
│ calculate_final_price(100) ← Step 1
│ base_price = 100        │
│ with_tax = 108          │
│ with_discount = 98      │
│ return: 98              │
└─────────────────────────┘
│ main()                  │
│ final = 98              │
└─────────────────────────┘
```

## Stack Overflow

If functions call each other too many times without returning, the stack runs out of space:

```python
def infinite_loop():
    return infinite_loop()  # Calls itself forever!

# This will crash with "RecursionError: maximum recursion depth exceeded"
```

**Visual representation:**
```
┌──────────────┐
│ infinite()   │
│ infinite()   │
│ infinite()   │
│ infinite()   │
│ ...          │  ← Stack keeps growing!
│ (1000+ calls)│
│ ...          │
└──────────────┘
Stack overflow! 💥
```

## Key Insights

1. **LIFO (Last In, First Out)**: The most recently called function finishes first
2. **Local Variables**: Each function call has its own separate variables
3. **Finite Size**: The stack has limited space, which is why infinite recursion fails
4. **Return Pops Frame**: When a function returns, its frame is removed from the stack

Understanding the call stack is crucial for debugging and for learning about recursion!
