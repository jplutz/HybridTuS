# Functions - Design Your Own Function

## Active Learning Exercise

In this activity, you'll **design and implement your own function** from a real-world problem description.

## The Problem

You're building a grade calculator for a school. You need a function that:
- Takes a numerical score (0-100)
- Returns a letter grade based on this scale:
  - 90-100: 'A'
  - 80-89: 'B'
  - 70-79: 'C'
  - 60-69: 'D'
  - Below 60: 'F'

## Your Task

### Step 1: Design the Function Signature

Fill in the blanks:

```
Function Name: _____________
Parameters: _____________
Return Type: _____________
```

### Step 2: Write Pseudocode

Before coding, write the logic in plain English:

```
1. Check if score is >= 90, return 'A'
2. ...
3. ...
```

### Step 3: Implement the Function

Write the actual code in your preferred language:

```javascript
// JavaScript example
function _____________(___________) {
    // Your code here

}
```

### Step 4: Test Your Function

Create test cases:

```javascript
console.log(getGrade(95));  // Should output: 'A'
console.log(getGrade(82));  // Should output: ?
console.log(getGrade(67));  // Should output: ?
console.log(getGrade(45));  // Should output: ?
```

## Solution

<details>
<summary>Click to reveal a sample solution</summary>

```javascript
function getGrade(score) {
    if (score >= 90) {
        return 'A';
    } else if (score >= 80) {
        return 'B';
    } else if (score >= 70) {
        return 'C';
    } else if (score >= 60) {
        return 'D';
    } else {
        return 'F';
    }
}

// Test cases
console.log(getGrade(95));  // Output: 'A'
console.log(getGrade(82));  // Output: 'B'
console.log(getGrade(67));  // Output: 'D'
console.log(getGrade(45));  // Output: 'F'
```

</details>

## Challenge Activity

**Extend the function** to handle edge cases:
1. What if the score is negative?
2. What if the score is greater than 100?
3. Can you add support for '+' and '-' grades (e.g., B+, B, B-)?

Try implementing these enhancements!

## Reflection Questions

1. Why is using a function better than repeating this logic multiple times in your code?
2. How would you modify this function if the grading scale changed?
3. What are the advantages of having a clear function signature (name, parameters, return type)?

## Key Takeaway

By actively designing and implementing functions, you learn to:
- Break down problems into reusable components
- Think about inputs, outputs, and edge cases
- Write modular, maintainable code
- Test and verify your logic

Functions are the building blocks of all programs—master them through practice!
