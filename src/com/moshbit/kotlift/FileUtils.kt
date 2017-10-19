package com.moshbit.kotlift

import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*

fun listFiles(srcFile: File, extension: String = ""): List<File> {
  // get all the files from a directory
  val fList = srcFile.let {
    if (it.isDirectory)
      it.listFiles()?.sorted()
    else
      readFileList(it)
  }

  val result = ArrayList<File>()
  if (fList != null) {
    for (file in fList) {
      if (file.isFile) {
        if (file.name.endsWith(extension)) {
          result.add(file)
        }
      } else if (file.isDirectory) {
        result.addAll(listFiles(file, extension))
      }
    }
  }
  return result
}

fun readFileList(file: File): List<File> {
  val lines = Files.readAllLines(Paths.get(file.path), Charsets.UTF_8)
  return lines.map { File(it) }.filter { it.exists() }
}