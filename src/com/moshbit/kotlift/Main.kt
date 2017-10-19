package com.moshbit.kotlift

import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.util.*

data class Replacement(val from: String, val to: String, val multiple: Boolean)

fun main(args: Array<String>) {
  if (args.count() < 4 || args.count() > 5) {
    println("Usage: kotlift src/kotlin sources.txt dest/swift replacementFile.json")
    println("or:    kotlift src/kotlin sources.txt dest/swift replacementFile.json dest/test/swift")
    println("calling with a test path validates all files")
    return
  }

  val sourceDir = File(args[0])
  if (!sourceDir.isDirectory) {
    println("Bad source dir: ${args[0]}")
    return
  }

  val sourcesTxtFile = File(args[1])
  if (!sourcesTxtFile.isFile) {
    println("Bad source files txt-file: ${args[1]}")
    return
  }

  val destinationDir = File(args[2])
  if (!destinationDir.exists() || !destinationDir.isDirectory) {
    println("Bad destination dir: ${args[2]}")
    return
  }

  // Replacement array
  val replacements: List<Replacement> = loadReplacementList((Paths.get(args[3]).toFile()))
  if (replacements.isEmpty()) {
    println("replacementFile empty: ${Paths.get(args[3]).toFile().absoluteFile}")
    return
  }

  val testDestinationDir = if (args.count() == 5)
    File(args[4])
  else
    null

  val destinationFiles = ArrayList<File>()
  val sourceFiles = listFiles(sourcesTxtFile, ".kt")

  println(replacements)
  println(sourceFiles)

  val transpiler = Transpiler(replacements)

  print("Parsing...    ")

  // Parse each file
  for (file in sourceFiles) {
    val lines = Files.readAllLines(Paths.get(file.path), Charsets.UTF_8)
    transpiler.parse(lines)
  }

  print(" finished\nTranspiling...")

  val destinationPath = destinationDir.canonicalPath
  // Transpile each file
  for (file in sourceFiles) {
    val lines = Files.readAllLines(Paths.get(file.path), Charsets.UTF_8)
    val destLines = transpiler.transpile(lines)

    val destPath = Paths.get(file.canonicalPath
        .replace(sourceDir.canonicalPath, destinationPath)
        .replace(".kt", ".swift"))
    val destFile = destPath.toFile()
    destinationFiles.add(destFile)

    Files.write(destPath, destLines, Charsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
  }

  // Validate
  if (testDestinationDir != null) {
    print(" finished\nValidating...  ")
    var errorCount = 0
    val testDestinationPath = testDestinationDir.canonicalPath
    // Validate each file
    for (destFile in destinationFiles) {
      val testPath = Paths.get(destFile.canonicalPath
          .replace(destinationPath, testDestinationPath))
      if (!testPath.toFile().exists()) {
        println("ERROR: test file not found: $testPath")
        continue
      }

      val linesDest = Files.readAllLines(destFile.toPath(), Charsets.UTF_8)
      val linesTest = Files.readAllLines(testPath, Charsets.UTF_8)

      // Check for same line count
      if (linesDest.count() != linesTest.count()) {
        errorCount++
        validateError(destFile.path,
            "Invalid line count: dest=${linesDest.count()} test=${linesTest.count()}")
      } else {
        // Compare each line
        for (j in 0..linesDest.count() - 1) {
          if (linesDest[j] != linesTest[j]) {
            errorCount++
            validateError(destFile.path, "\n  \"${linesDest[j]}\"\n  \"${linesTest[j]}\"")
          }
        }
      }
    }

    if (errorCount == 0) {
      println("finished - everything OK")
    } else {
      println("finished - $errorCount ERRORS")
    }
  } else {
    println(" finished")
  }
}

// A very simple json parser. Could also be implemented with jackson json parser, but omitted to reduce dependencies.
fun loadReplacementList(file: File): List<Replacement> {
  val lines = Files.readAllLines(Paths.get(file.path), Charsets.UTF_8)
  val list = LinkedList<Replacement>()

  val replacementRegex = Regex("\\s*\\{\\s*\\\"from\\\":\\s*\\\"(.*)\\\",\\s*\\\"to\\\":\\s*\\\"(.*)\\\",\\s*\\\"multiple\\\":\\s*\\\"?(true|false)\\\"?\\s*\\},?\\s*")

  // Simple json parser
  for (line in lines) {
    if (line.matches(replacementRegex)) {
      list.add(Replacement(
          line.replace(replacementRegex, "$1").replace("\\\"", "\"").replace("\\\\", "\\"),
          line.replace(replacementRegex, "$2").replace("\\\\", "\\"),
          line.replace(replacementRegex, "$3").equals("true")))
    }
  }

  return list
}

fun validateError(fileName: String, hint: String) {
  println("$fileName: $hint")
}
