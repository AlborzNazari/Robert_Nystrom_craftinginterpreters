//> Scanning scanner-class
package com.craftinginterpreters.lox;

import java.util.ArrayList;  // Import list data structure
import java.util.HashMap;    // Import hash map for keyword lookup
import java.util.List;       // Import List interface
import java.util.Map;        // Import Map interface

// Static import: brings TokenType enum values directly into scope
// So we can write AND, OR, VAR instead of TokenType.AND, etc.
import static com.craftinginterpreters.lox.TokenType.*;

/**
 * SCANNER CLASS
 * Purpose: Convert raw source code (string) into a list of tokens (lexemes)
 * 
 * Flow: Source text → Scanner.scanTokens() → List<Token>
 * Example: "var x = 5;" → [VAR, IDENTIFIER, EQUAL, NUMBER, SEMICOLON, EOF]
 */
class Scanner {

  // ============================================================================
  // KEYWORD MAP: Maps reserved words to their token types
  // ============================================================================
  
  private static final Map<String, TokenType> keywords;
  // ↑ "static final" means: shared by all Scanner instances, can't be reassigned

  // Static initializer block: runs once when the class loads
  // This populates the keywords map before any Scanner object is created
  static {
    keywords = new HashMap<>();
    // Each reserved word maps to its token type
    keywords.put("and",    AND);      // "and" operator
    keywords.put("class",  CLASS);    // Class declaration
    keywords.put("else",   ELSE);     // Else branch
    keywords.put("false",  FALSE);    // Boolean literal
    keywords.put("for",    FOR);      // Loop keyword
    keywords.put("fun",    FUN);      // Function declaration
    keywords.put("if",     IF);       // Conditional
    keywords.put("nil",    NIL);      // Null value
    keywords.put("or",     OR);       // Or operator
    keywords.put("print",  PRINT);    // Print statement
    keywords.put("return", RETURN);   // Return statement
    keywords.put("super",  SUPER);    // Super keyword
    keywords.put("this",   THIS);     // This keyword
    keywords.put("true",   TRUE);     // Boolean literal
    keywords.put("var",    VAR);      // Variable declaration
    keywords.put("while",  WHILE);    // Loop keyword
  }
  // When identifier() method runs later, it checks this map to see if the
  // identifier is actually a reserved word

  // ============================================================================
  // SCANNER STATE: Instance variables that track position while scanning
  // ============================================================================

  private final String source;
  // ↑ "final" = immutable. Once set in constructor, never changes.
  // Holds the entire input string (e.g., "var x = 5;")

  private final List<Token> tokens = new ArrayList<>();
  // ↑ "final" = immutable reference, but the list contents change
  // We ADD tokens to this list as we scan. It grows as we progress.
  // Returned to caller at the end of scanTokens()

  // ============================================================================
  // CORE SCANNING POINTERS
  // ============================================================================

  private int start = 0;
  // ↑ Points to the first character of the current lexeme being scanned
  // Example: scanning "var", start = 0 (points to 'v')
  // Resets at the beginning of each iteration in scanTokens()

  private int current = 0;
  // ↑ Points to the next character to be examined
  // Incremented by advance() as we consume characters
  // Example: scanning "var", current moves 0 → 1 → 2 → 3 (after 'r')

  private int line = 1;
  // ↑ Tracks the current line number for error reporting
  // Incremented when we see '\n'
  // Helps users know WHERE in the code an error occurred

  // ============================================================================
  // CONSTRUCTOR: Initialize scanner with source code
  // ============================================================================

  Scanner(String source) {
    // Called when creating a new Scanner object
    // Example: new Scanner("var x = 5;")
    
    this.source = source;
    // Store the input string. All scanning methods will read from this.
  }

  // ============================================================================
  // MAIN SCANNING LOOP
  // ============================================================================

  List<Token> scanTokens() {
    // Called once from the main interpreter
    // Returns a complete list of all tokens in the source code
    
    while (!isAtEnd()) {
      // Loop until we reach the end of the source string
      // !isAtEnd() = "not at end" = "there are still characters to scan"
      
      start = current;
      // Mark the beginning of the NEXT lexeme
      // Every iteration starts fresh: we're about to scan a new token
      // Example iteration 1: start = 0 (start of "var")
      //        iteration 2: start = 3 (start of "x")
      //        iteration 3: start = 5 (start of "=")
      
      scanToken();
      // Process ONE token. Advances current until the lexeme is complete.
      // Adds the token to the tokens list.
      // Then loops back to scanTokens() for the next lexeme.
    }
    // Loop exits when current >= source.length() (isAtEnd() returns true)

    tokens.add(new Token(EOF, "", null, line));
    // Add the EOF (End-Of-File) token at the end
    // This signals to the parser: "No more tokens coming"
    // Example: [VAR, IDENTIFIER, EQUAL, NUMBER, SEMICOLON, EOF]
    
    return tokens;
    // Return the complete token list to the caller (main interpreter)
  }

  // ============================================================================
  // SCAN ONE TOKEN: The workhorse method
  // ============================================================================

  private void scanToken() {
    // Called repeatedly from scanTokens() to process one lexeme at a time
    // When called: start is set, current is at the start of the lexeme
    // When done: current has moved past the lexeme, a token is added
    
    char c = advance();
    // Read the FIRST character of this lexeme and move current forward
    // advance() returns source.charAt(current) THEN increments current
    // Example: if source = "var x = 5" and current = 0
    //          c = 'v', then current becomes 1

    // SWITCH statement: decides what to do based on the character
    // Each case handles a specific character or set of characters
    switch (c) {

      // ====== SINGLE-CHARACTER TOKENS ======
      // These are straightforward: one char = one token
      
      case '(':
        // Left parenthesis: "()"
        addToken(LEFT_PAREN);
        // Create a token of type LEFT_PAREN, add to tokens list
        break;
      // If we see '(', emit a LEFT_PAREN token and we're done with this lexeme

      case ')':
        // Right parenthesis: ")"
        addToken(RIGHT_PAREN);
        break;

      case '{':
        // Left brace: "{"
        addToken(LEFT_BRACE);
        break;

      case '}':
        // Right brace: "}"
        addToken(RIGHT_BRACE);
        break;

      case ',':
        // Comma: ","
        addToken(COMMA);
        break;

      case '.':
        // Dot: "." (used for decimal numbers AND method calls)
        addToken(DOT);
        break;

      case '-':
        // Minus: "-"
        addToken(MINUS);
        break;

      case '+':
        // Plus: "+"
        addToken(PLUS);
        break;

      case ';':
        // Semicolon: ";"
        addToken(SEMICOLON);
        break;

      case '*':
        // Star (multiplication): "*"
        addToken(STAR);
        break;

      // ====== TWO-CHARACTER OPERATORS ======
      // These might be one or two characters. Use lookahead to decide.
      
      case '!':
        // Exclamation mark: "!" or "!="
        // match('=') checks if the NEXT character is '='
        // If yes: consume it and emit BANG_EQUAL (!=)
        // If no: just emit BANG (!)
        addToken(match('=') ? BANG_EQUAL : BANG);
        break;
        // Example: source = "!= 5"
        //          c = '!', current = 1
        //          match('=') looks at source[1] = '=' → true
        //          current becomes 2
        //          emit BANG_EQUAL token

      case '=':
        // Equals: "=" or "=="
        // Same logic: check if next char is '='
        addToken(match('=') ? EQUAL_EQUAL : EQUAL);
        break;

      case '<':
        // Less than: "<" or "<="
        addToken(match('=') ? LESS_EQUAL : LESS);
        break;

      case '>':
        // Greater than: ">" or ">="
        addToken(match('=') ? GREATER_EQUAL : GREATER);
        break;

      // ====== SLASH AND COMMENTS ======
      // "/" is special: could be division OR the start of a comment
      
      case '/':
        // Forward slash: "/" or start of comment "//"
        if (match('/')) {
          // Next character is also '/': this is a comment!
          // Comments go from "//" to the end of the line
          
          while (peek() != '\n' && !isAtEnd()) {
            // Keep consuming characters until we hit a newline or EOF
            // peek() looks at current character without consuming it
            // We keep advancing through the comment text
            advance();
            // Each advance() consumes one character of the comment
          }
          // Loop exits when: we see '\n' OR reach end of file
          
          // DON'T call addToken() for comments
          // Comments are not meaningful tokens; the parser doesn't need them
          // They disappear; only the token list matters
        } else {
          // Next character is NOT '/': this is division operator
          addToken(SLASH);
        }
        break;

      // ====== WHITESPACE ======
      // These characters separate tokens but aren't tokens themselves
      
      case ' ':
      case '\r':
      case '\t':
        // Space, carriage return, tab: ignore them
        // Don't emit a token. Just move on.
        // The loop resets, and we scan the next character.
        break;

      case '\n':
        // Newline: track it for error reporting
        line++;
        // Increment line counter
        // Later, if there's an error, we can tell the user which line
        break;

      // ====== STRING LITERALS ======
      
      case '"':
        // Double quote: start of a string literal
        // Call string() method to handle the full string
        string();
        // string() will consume characters until it finds the closing "
        // It will also handle multi-line strings (updating line counter)
        break;

      // ====== DEFAULT: ANYTHING ELSE ======
      
      default:
        // We got a character that didn't match any case above
        // Could be a digit (start of number), letter (start of identifier),
        // or invalid character (@, #, etc.)
        
        if (isDigit(c)) {
          // c is a digit (0-9)
          // This is the start of a number literal
          number();
          // number() method will consume all digits and handle decimals
          // Example: "42" or "3.14"
        } else if (isAlpha(c)) {
          // c is a letter (a-z, A-Z) or underscore (_)
          // This is the start of an identifier or keyword
          identifier();
          // identifier() will consume all alphanumeric characters
          // Then check if it's a reserved word
          // Example: "var", "x", "orchid"
        } else {
          // c is none of the above: invalid character
          Lox.error(line, "Unexpected character.");
          // Report error to the user with line number
          // But KEEP SCANNING to find other errors
          // advance() already moved current forward, so no infinite loop
        }
        break;
    }
    // End of switch. scanToken() is done. Control returns to scanTokens()
  }

  // ============================================================================
  // IDENTIFIER AND KEYWORD HANDLING
  // ============================================================================

  private void identifier() {
    // Called when we encounter the first letter of an identifier or keyword
    // At entry: start points to the first letter, current is one past it
    
    while (isAlphaNumeric(peek())) {
      // Keep consuming while the next character is alphanumeric
      // peek() looks at current without advancing
      // isAlphaNumeric() checks: letter OR digit OR underscore
      
      advance();
      // Consume the character. current moves forward.
    }
    // Loop exits when peek() is NOT alphanumeric (space, operator, etc.)

    // Now we have the complete identifier/keyword
    // Extract it as a string
    String text = source.substring(start, current);
    // Example: start = 3, current = 6, source = "var x = 5"
    //          text = "var"

    // Check if this text is a reserved keyword
    TokenType type = keywords.get(text);
    // keywords.get("var") returns TokenType.VAR
    // keywords.get("x") returns null (not a keyword)

    if (type == null) {
      // Not found in keywords map: it's a user-defined identifier
      type = IDENTIFIER;
      // Set type to generic IDENTIFIER
    }
    // If it WAS found, type is already set to the keyword token type

    addToken(type);
    // Emit the token (either IDENTIFIER or a specific keyword type)
  }

  // ============================================================================
  // NUMBER LITERAL HANDLING
  // ============================================================================

  private void number() {
    // Called when we encounter the first digit of a number
    // At entry: start points to the first digit, current is one past it
    
    while (isDigit(peek())) {
      // Consume all leading digits (the integer part)
      advance();
    }
    // Loop exits when peek() is not a digit

    // Now check for a decimal point
    if (peek() == '.' && isDigit(peekNext())) {
      // Two conditions: next char is '.' AND the char after that is a digit
      // This ensures we don't consume a trailing dot (3. is invalid)
      
      advance();
      // Consume the '.'
      // Now current points to the first digit after the decimal point
      
      while (isDigit(peek())) {
        // Consume all digits after the decimal point (the fractional part)
        advance();
      }
      // Loop exits when peek() is not a digit
    }
    // If we didn't have a decimal point, we skip this block

    // Now convert the lexeme (string) to an actual double value
    addToken(NUMBER,
        Double.parseDouble(source.substring(start, current)));
    // substring(start, current) extracts the full number string
    // Example: "3.14" or "42"
    // Double.parseDouble() converts it to a Java double
    // The double becomes the token's literal value
    // Later, the interpreter will use this actual numeric value
  }

  // ============================================================================
  // STRING LITERAL HANDLING
  // ============================================================================

  private void string() {
    // Called when we encounter the opening " of a string literal
    // At entry: current is one past the opening "
    
    while (peek() != '"' && !isAtEnd()) {
      // Consume characters until we find the closing " OR reach EOF
      // Two conditions: NOT end quote AND NOT end of file
      
      if (peek() == '\n') {
        // If we encounter a newline inside the string
        line++;
        // Update the line counter (Lox supports multi-line strings)
      }
      
      advance();
      // Consume the character (newline, letter, space, anything)
    }
    // Loop exits when: we see " OR we reach end of file

    if (isAtEnd()) {
      // We reached EOF without finding the closing "
      Lox.error(line, "Unterminated string.");
      // Report error: user forgot to close the string
      return;
      // Exit this method without emitting a token
      // The error flag is set, so the interpreter won't run the code
    }

    advance();
    // Consume the closing "
    // Now current is one past the closing "

    // Extract the string value WITHOUT the surrounding quotes
    String value = source.substring(start + 1, current - 1);
    // start + 1 skips the opening "
    // current - 1 excludes the closing "
    // Example: source = "hello world"
    //          start = 0, current = 13
    //          value = source.substring(1, 12) = "hello world"

    addToken(STRING, value);
    // Emit the token with the actual string content as the literal
  }

  // ============================================================================
  // LOOKAHEAD: CONDITIONAL CHARACTER CONSUMPTION
  // ============================================================================

  private boolean match(char expected) {
    // Check if the current character matches expected
    // If it does, consume it and return true
    // If it doesn't, leave it and return false
    // Used for multi-character operators like !=, <=, ==
    
    if (isAtEnd()) {
      // Already at end of file: nothing to match
      return false;
    }

    if (source.charAt(current) != expected) {
      // Current character doesn't match what we're looking for
      return false;
      // Don't advance. Leave current as is.
    }

    current++;
    // Current character DOES match. Consume it.
    return true;
  }

  // ============================================================================
  // LOOKAHEAD: PEEK WITHOUT CONSUMING
  // ============================================================================

  private char peek() {
    // Look at the current character WITHOUT consuming it
    // Used to examine the next character in the lookahead
    
    if (isAtEnd()) {
      // Already at end of file: no character to peek
      return '\0';
      // Return null character (ASCII 0)
      // This signals "no character here"
    }

    return source.charAt(current);
    // Return the character at current WITHOUT advancing
    // peek() can be called multiple times; it always returns the same char
  }

  private char peekNext() {
    // Look TWO characters ahead WITHOUT consuming
    // Used when we need to look past the next character
    // Example: checking for digits after a decimal point (3.14)
    
    if (current + 1 >= source.length()) {
      // The character after current is past the end of the file
      return '\0';
      // Return null character: "no character there"
    }

    return source.charAt(current + 1);
    // Return the character one position ahead
  }

  // ============================================================================
  // CHARACTER CLASSIFICATION: Helper methods
  // ============================================================================

  private boolean isAlpha(char c) {
    // Check if c is a letter or underscore
    // Used to identify the start of an identifier
    
    return (c >= 'a' && c <= 'z') ||    // lowercase letter
           (c >= 'A' && c <= 'Z') ||    // uppercase letter
            c == '_';                    // underscore
  }

  private boolean isAlphaNumeric(char c) {
    // Check if c is a letter, digit, or underscore
    // Used to continue consuming an identifier
    
    return isAlpha(c) || isDigit(c);
    // True if it's alpha OR digit
  }

  private boolean isDigit(char c) {
    // Check if c is a digit (0-9)
    // Used to identify the start of a number literal
    
    return c >= '0' && c <= '9';
  }

  // ============================================================================
  // EOF CHECK: Have we reached the end?
  // ============================================================================

  private boolean isAtEnd() {
    // Check if current is at or past the end of the source
    // Used in multiple places to prevent reading past the end
    
    return current >= source.length();
    // source.length() is the position AFTER the last character
    // When current reaches it, we're at EOF
  }

  // ============================================================================
  // CHARACTER CONSUMPTION: Move forward
  // ============================================================================

  private char advance() {
    // Consume the current character and move to the next one
    // Returns the character that was just consumed
    // Used to step through the source one character at a time
    
    return source.charAt(current++);
    // source.charAt(current) gets the char at current
    // current++ increments current AFTER reading
    // The char is returned to the caller
    // Example: current = 0, advance() returns char at 0, current becomes 1
  }

  // ============================================================================
  // TOKEN CREATION: Add a token to the list
  // ============================================================================

  private void addToken(TokenType type) {
    // Overloaded version: no literal value (for operators, keywords with no value)
    addToken(type, null);
    // Calls the full version with null literal
  }

  private void addToken(TokenType type, Object literal) {
    // Create a token and add it to the tokens list
    // Called whenever we've identified a complete lexeme
    
    String text = source.substring(start, current);
    // Extract the lexeme text (the actual characters from the source)
    // start marks the beginning, current marks the end
    // Example: "var" or "42" or "="

    tokens.add(new Token(type, text, literal, line));
    // Create a new Token with:
    //   - type: TokenType (VAR, IDENTIFIER, NUMBER, etc.)
    //   - text: The lexeme string
    //   - literal: The actual value (e.g., 42.0 for "42", "hello" for string)
    //   - line: The line number (for error reporting)
    // Add it to the tokens list

    // Now the token is recorded. Next iteration will scan the next lexeme.
  }

} // End of Scanner class
