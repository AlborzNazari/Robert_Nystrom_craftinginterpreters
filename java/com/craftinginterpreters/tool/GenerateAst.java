// ============================================================================
// NOTE ON THIS FILE
// ----------------------------------------------------------------------------
// This is a CODE GENERATOR, not part of the interpreter itself.
// It is a tiny standalone program whose ONLY job is to write OTHER .java files.
// You run it once, by hand, during development. Its output (Expr.java and
// Stmt.java) is then compiled together with Scanner, Parser, Interpreter, etc.
//
// Note the package: com.craftinginterpreters.TOOL  (not .lox)
// The interpreter lives in .lox. This tool lives apart on purpose: it never
// ships, it never runs when someone runs a Lox program. It is a build-time
// scaffold, like a Makefile that happens to emit Java.
// ============================================================================

package com.craftinginterpreters.tool;

import java.io.IOException;     // thrown if we can't open/write the output file
import java.io.PrintWriter;     // our pen: writes lines of text to a file
import java.util.Arrays;        // Arrays.asList(...) to build the type lists inline
import java.util.List;          // the List interface for those type descriptions

/**
 * GENERATE-AST
 * Purpose: emit the boilerplate Java source for the Abstract Syntax Tree.
 *
 * Flow: a List<String> describing classes  ->  defineAst()  ->  Expr.java / Stmt.java
 * Example input string:  "Binary : Expr left, Token operator, Expr right"
 * Example output: a full `static class Binary extends Expr { ... }` written to disk.
 *
 * Why this exists: the AST has ~20 node classes that are 95% identical. Each is
 * just (a) a name, (b) a list of fields, (c) a constructor that copies those
 * fields, (d) an accept() method. Hand-writing 20 of those is tedious and a
 * great place to introduce typos. So we describe each class in ONE line and let
 * the machine stamp out the repetitive Java. This is metaprogramming: a program
 * that writes a program.
 */
public class GenerateAst {

  // ==========================================================================
  // ENTRY POINT
  // ==========================================================================

  public static void main(String[] args) throws IOException {
    // This program is run from the command line, e.g.:
    //   java com.craftinginterpreters.tool.GenerateAst ./src/.../lox
    // The single argument is the directory where the generated files should land.

    if (args.length != 1) {
      // Defensive check: we need exactly one argument (the output directory).
      System.err.println("Usage: generate_ast <output directory>");
      System.exit(64);
      // 64 is the Unix "command line usage error" exit code (from sysexits.h).
      // Nystrom uses these conventional codes throughout the book.
    }

    String outputDir = args[0];
    // Where to write Expr.java and Stmt.java.

    // ------------------------------------------------------------------------
    // EXPRESSIONS: things that PRODUCE A VALUE  (a + b, foo(), 3.14, x, -y)
    // ------------------------------------------------------------------------
    // Each string is:  ClassName : type field, type field, ...
    // The whitespace padding is purely cosmetic (lines up nicely); .trim()
    // later removes it. The COLON is the real separator the parser below relies on.
    defineAst(outputDir, "Expr", Arrays.asList(
      "Assign   : Token name, Expr value",                       // x = value
      "Binary   : Expr left, Token operator, Expr right",        // a + b, a < b
      "Call     : Expr callee, Token paren, List<Expr> arguments", // f(1, 2)
      "Get      : Expr object, Token name",                      // object.field
      "Grouping : Expr expression",                              // ( expr )
      "Literal  : Object value",                                 // 42, "hi", true, nil
      "Logical  : Expr left, Token operator, Expr right",        // a and b, a or b
      "Set      : Expr object, Token name, Expr value",          // object.field = value
      "Super    : Token keyword, Token method",                  // super.method
      "This     : Token keyword",                                // this
      "Unary    : Token operator, Expr right",                   // !x, -x
      "Variable : Token name"                                    // bare x
    ));

    // ------------------------------------------------------------------------
    // STATEMENTS: things that DO SOMETHING but produce no value
    //             (print x;  var y = 1;  while (c) {...};  if/else; fun decls)
    // ------------------------------------------------------------------------
    // Note the string concatenation on the long lines: Java has no multi-line
    // string literals (pre-text-blocks), so we glue two pieces with + . The
    // result is still ONE description string; the line break is just for our eyes.
    defineAst(outputDir, "Stmt", Arrays.asList(
      "Block      : List<Stmt> statements",                      // { ... }
      "Class      : Token name, Expr.Variable superclass," +     // class C < Base { ... }
                  " List<Stmt.Function> methods",
      "Expression : Expr expression",                            // an expr used as a stmt: foo();
      "Function   : Token name, List<Token> params," +          // fun f(a, b) { ... }
                  " List<Stmt> body",
      "If         : Expr condition, Stmt thenBranch," +          // if (c) a; else b;
                  " Stmt elseBranch",
      "Print      : Expr expression",                            // print expr;
      "Return     : Token keyword, Expr value",                  // return expr;
      "Var        : Token name, Expr initializer",               // var x = expr;
      "While      : Expr condition, Stmt body"                   // while (c) body;
    ));
    // After main() returns, two files exist on disk: Expr.java and Stmt.java.
    // The tool's job is done. It will never run again until you change the AST.
  }

  // ==========================================================================
  // defineAst : write ONE complete base file (either Expr.java or Stmt.java)
  // ==========================================================================

  private static void defineAst(
      String outputDir, String baseName, List<String> types)
      throws IOException {
    // baseName is "Expr" or "Stmt". types is the list of "Name : fields" strings.

    String path = outputDir + "/" + baseName + ".java";
    // e.g. "./.../lox/Expr.java"

    PrintWriter writer = new PrintWriter(path, "UTF-8");
    // Open the file for writing. Everything below is us TYPING JAVA AS TEXT.
    // Read these println() calls as: "emit this exact line into the new file."

    // --- file header ---
    writer.println("package com.craftinginterpreters.lox;");
    // IMPORTANT: the GENERATED file belongs to .lox, even though this generator
    // lives in .tool. The output is part of the interpreter; the generator isn't.
    writer.println();
    writer.println("import java.util.List;");
    // Some node fields are List<...> (e.g. Call.arguments), so the output needs this.
    writer.println();
    writer.println("abstract class " + baseName + " {");
    // -> "abstract class Expr {"   (abstract: you never make a bare Expr, only
    //    a Binary, Literal, etc. The base type exists so they share a type.)

    defineVisitor(writer, baseName, types);
    // Emit the nested Visitor<R> interface (explained in its own method below).

    // --- the concrete node classes ---
    for (String type : types) {
      String className = type.split(":")[0].trim();
      // Left of the colon, trimmed: "Binary".
      String fields = type.split(":")[1].trim();
      // Right of the colon, trimmed: "Expr left, Token operator, Expr right".
      defineType(writer, baseName, className, fields);
      // Emit one full static nested class for this node.
    }

    // --- the abstract accept() the whole hierarchy must implement ---
    writer.println();
    writer.println("  abstract <R> R accept(Visitor<R> visitor);");
    // Every node will override this. <R> is a generic return type so the same
    // tree can be visited by an Interpreter (returns Object), a Resolver
    // (returns Void), a pretty-printer (returns String), etc. This single line
    // is the hinge the Visitor pattern swings on. See accept-method below.

    writer.println("}");
    // Close the base class.

    writer.close();
    // Flush and close the file. PrintWriter buffers; forgetting this can leave
    // the file empty or truncated. (Real-world footgun.)
  }

  // ==========================================================================
  // defineVisitor : emit the  interface Visitor<R> { ... }  block
  // ==========================================================================

  private static void defineVisitor(
      PrintWriter writer, String baseName, List<String> types) {
    writer.println("  interface Visitor<R> {");
    // -> "  interface Visitor<R> {"
    // R is the return type the visitor produces. One method PER node type.

    for (String type : types) {
      String typeName = type.split(":")[0].trim();
      // "Binary", "Literal", ...

      writer.println("    R visit" + typeName + baseName + "(" +
          typeName + " " + baseName.toLowerCase() + ");");
      // Builds a line like:
      //   "    R visitBinaryExpr(Binary expr);"
      //   "    R visitLiteralExpr(Literal expr);"
      // Naming convention: visit<Type><Base>. The parameter is named after the
      // base ("expr" / "stmt"). Any class that wants to process the AST
      // implements THIS interface and gets a compiler-enforced checklist: it
      // MUST handle every node type or it won't compile. That's the payoff.
    }

    writer.println("  }");
    // Close the interface.
  }

  // ==========================================================================
  // defineType : emit ONE concrete node class (Binary, Literal, Var, ...)
  // ==========================================================================

  private static void defineType(
      PrintWriter writer, String baseName,
      String className, String fieldList) {
    // fieldList example: "Expr left, Token operator, Expr right"

    writer.println("  static class " + className + " extends " +
        baseName + " {");
    // -> "  static class Binary extends Expr {"
    // static nested class: it doesn't need an enclosing Expr instance; it's
    // just namespaced under Expr so you write Expr.Binary.

    // ---- constructor ----
    writer.println("    " + className + "(" + fieldList + ") {");
    // -> "    Binary(Expr left, Token operator, Expr right) {"

    String[] fields = fieldList.split(", ");
    // Split into individual "type name" pairs:
    //   ["Expr left", "Token operator", "Expr right"]

    for (String field : fields) {
      String name = field.split(" ")[1];
      // field = "Expr left"  ->  split on space  ->  ["Expr", "left"]  ->  [1] = "left"
      // We want the field NAME (index 1), not its type (index 0).

      writer.println("      this." + name + " = " + name + ";");
      // -> "      this.left = left;"
      // The classic "copy each constructor parameter into a field" boilerplate.
    }

    writer.println("    }");
    // Close the constructor.

    // ---- accept(): the Visitor pattern's dispatch point ----
    writer.println();
    writer.println("    @Override");
    writer.println("    <R> R accept(Visitor<R> visitor) {");
    writer.println("      return visitor.visit" +
        className + baseName + "(this);");
    // -> "      return visitor.visitBinaryExpr(this);"
    // THIS is the whole trick. When you call someNode.accept(v), Java's virtual
    // dispatch already knows the node's REAL type (Binary), so it lands in
    // Binary.accept, which then calls v.visitBinaryExpr(this). Two dispatches:
    //   1. node.accept(...)        -> picks the right accept by node type
    //   2. visitor.visitBinaryExpr -> picks the right behavior by visitor
    // That's "double dispatch", and it's how we get type-specific behavior
    // WITHOUT a giant `if (node instanceof Binary) ... else if ...` chain.
    writer.println("    }");

    // ---- fields ----
    writer.println();
    for (String field : fields) {
      writer.println("    final " + field + ";");
      // -> "    final Expr left;"  etc.
      // final: AST nodes are immutable once built. A parsed tree never mutates;
      // that immutability makes the interpreter and resolver far easier to reason
      // about (no node changing under your feet between passes).
    }

    writer.println("  }");
    // Close this node class.
  }
}
