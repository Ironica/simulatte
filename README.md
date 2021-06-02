# Simulatte

A Playground server implemented with Kotlin DSL.

Inspired by Playgrounds on iOS/macOS and Karel the Robot, the project was first implemented using ANTLR. Later, it was
migrated to the usage of Kotlin DSL.

See docs for how to launch the application, some test cases and a short language reference on Kotlin DSL used (a subset
of Kotlin language) by the application. These docs might be helpful for you to implement your
own client to communicate with the server.

**Warning**: The project is still in development and has great tons of bugs.

For example, by the moment you can't quit the program but you have to close the terminal in order to quit.

This project is part of the Project ironica.

Build with

- Kotlin
- Ktor
- Kotlin serialization
- Kotlin Poet
- Ki-Shell

For more information please review [the Wiki](https://github.com/kokoro-aya/amatsukaze/wiki) of this repo.

For download please go to the pages of [Release](https://github.com/kokoro-aya/amatsukaze/releases).

---

# Getting Started

### Prerequisite

Simulatte requires Java version of range 11 to 15. At the moment, since Kotlin is not compatible with Java 16, this version is not supported. According to your OS platform, you can use `choco`, `brew` or `apt-get` or other package manager to get the latest OpenJDK with `adoptopenjdk`.

You could download the latest release of Simulatte in the [release](https://github.com/Ironica/simulatte/releases) column of this repo.

To use the server you may need to implement and setup your own client. However, you can download the [Postman](https://www.postman.com/downloads/) to launch requests and receive responses from the Simulatte server, for the debug purpose. By the way, you have to implement a feature to quit the server.

### Start Simulatte

**Warning: Please ensure that only one instance of your simulatte.jar is running otherwise you might need to force-quit some instances!!**

You cannot double-click the Simulatte program to launch it.

To launch it, open a terminal in the folder where you downloaded simulatte.jar and enter `java -jar simulatte.jar` where `simulatte.jar` should be replaced by your program's name.

It's recommended to run the program in a new terminal session, considering it's potential large outputs and by the way you cannot quit the program by clicking Ctrl + C so you have to close the terminal window directly in case you haven't implemented the quit feature.

#### Start Arguments

-   `debug` The server will output the playground's grid after each turn
-   `stdout` The server will output every user's output with `print(vararg)` method
    -   Notice that the `print(vararg)` method is different to `println()` method or `print(Int)` method. To call this method if you have only one element to print, use the syntax of `print("${}")`.
-   `output` The server will pretty print the concatenated code generated by codegen, for your debug purpose.
-   You can combine these arguments. Any order is possible. Any other argument will be ignored.

### Quit Simulatte

You cannot quit the program directly.

If you haven't implemented the quit feature, you should close the terminal window to quit the program. If you have the Postman installed, send a POST request to `127.0.0.1:9370/simulatte/shutdown`  to shutdown safely the server.

You can implement the quit feature in your client by using the same method.
