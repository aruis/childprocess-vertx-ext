# Child Process extension for Vert.x

Child Process is a Vert.x component for spawning OS child processes.

* based on https://github.com/brettwooldridge/NuProcess _Low-overhead, non-blocking I/O, external Process implementation for Java_.
* standard streams are fully non blocking
* spawned process can be killed

## Using Child Process

To use Child Process, add the following dependency to the _dependencies_ section of your build descriptor:

* Maven (in your `pom.xml`):

```xml
<dependency>
 <groupId>com.julienviet</groupId>
 <artifactId>childprocess-vertx-ext</artifactId>
 <version>1.3.0</version>
</dependency>
```

* Gradle (in your `build.gradle` file):

```ruby
dependencies {
 compile 'com.julienviet:childprocess-vertx-ext:1.3.0'
}
```

## Spawning child processes

You can spawn child processes with the [`Process.spawn`](../../yardoc/Childprocess/Process.html#spawn-class_method) method:

```ruby
require 'childprocess/process'
Childprocess::Process.spawn(vertx, "ls")

```

you can give arguments to child processes

```ruby
require 'childprocess/process'
Childprocess::Process.spawn(vertx, "ls", ["-lh", "/usr"])

```

by default child processes use the current process environment options, you can pass key-value pairs
as new environment variables

```ruby
require 'childprocess/process'
env = Hash.new()
env["MY_VAR"] = "whatever"
Childprocess::Process.spawn(vertx, "ls", {
  'env' => env
})

```

[`Process.env`](../../yardoc/Childprocess/Process.html#env-class_method) gives you the current process environment key-value pairs

```ruby
require 'childprocess/process'
options = {
  'env' => Childprocess::Process.env()
}
Childprocess::Process.spawn(vertx, "ls", options)

```

By default, the child processes uses the current process _current working directory_, the
[`cwd`](../dataobjects.html#ProcessOptions#set_cwd-instance_method) option overrides it

```ruby
require 'childprocess/process'
options = {
  'cwd' => "/some-dir"
}
Childprocess::Process.spawn(vertx, "ls", options)

```

## Interacting with child processes

The child process streams are available as

* [`stdin`](../../yardoc/Childprocess/Process.html#stdin-instance_method)
* [`stdout`](../../yardoc/Childprocess/Process.html#stdout-instance_method)
* [`stderr`](../../yardoc/Childprocess/Process.html#stderr-instance_method)

```ruby
require 'childprocess/process'
require 'vertx/buffer'
process = Childprocess::Process.spawn(vertx, "cat")

process.stdout().handler() { |buff|
  puts buff.to_string()
}

process.stdin().write(Vertx::Buffer.buffer("Hello World"))

```

Calling [`kill`](../../yardoc/Childprocess/Process.html#kill-instance_method) kills the child process, on POSIX it sends the
`SIGTERM` signal.

```ruby
require 'childprocess/process'
require 'vertx/buffer'
process = Childprocess::Process.spawn(vertx, "cat")

process.stdout().handler() { |buff|
  puts buff.to_string()
}

process.stdin().write(Vertx::Buffer.buffer("Hello World"))

# Kill the process
process.kill()

```

Child processes can also be forcibly killed

```ruby
require 'childprocess/process'
require 'vertx/buffer'
process = Childprocess::Process.spawn(vertx, "cat")

process.stdout().handler() { |buff|
  puts buff.to_string()
}

process.stdin().write(Vertx::Buffer.buffer("Hello World"))

# Kill the process forcibly
process.kill(true)

```

## Child process lifecycle

You can be aware of the child process termination

```ruby
require 'childprocess/process'
process = Childprocess::Process.spawn(vertx, "sleep", ["2"])

process.exit_handler() { |code|
  puts "Child process exited with code: #{code}"
}

```

## Delayed start

Calling [`Process.spawn`](../../yardoc/Childprocess/Process.html#spawn-class_method) starts the process after the current event loop task
execution, so you can set handlers on the process without a race condition.

Sometimes you want to delay the start of the child process you've created, for instance you are creating a process
from a non Vert.x thread:

```ruby
require 'childprocess/process'
process = Childprocess::Process.create(vertx, "echo \"Hello World\"")

process.stdout().handler() { |buff|
  puts buff.to_string()
}

# Start the process
process.start()

```