# PrickleMC — Unofficial Fabric Port for Minecraft 1.20.1

Community port by JustHari01, based on [Darkhax's original PrickleMC source](https://github.com/Darkhax-Minecraft/PrickleMC/tree/1.21.1). This is an independent backport and is not an official upstream release. The source and binary are distributed under the upstream LGPL-2.1 license; see [LICENSE](LICENSE) and [PORT_NOTICE.md](PORT_NOTICE.md).

This port keeps Prickle's JSON configuration format, comments, decorators, validation, and supported adapter API. Minecraft-specific codecs are adapted to the 1.20.1 APIs. It targets Fabric Loader on Minecraft 1.20.1 and Java 17.

Prickle is a lightweight configuration format based on JSON. Prickle aims
to be simple, accessible to everyday users, and be very easy to parse and
generate with code. This library implements the Prickle format as a
library that is compatible with Minecraft and various popular mod loaders.

## Config Format

### Properties

In Prickle the value of all properties are wrapped in JSON objects. This
allows metadata and other properties to be associated with that property.
The value of the property is held by the `value` property. The value must
be a valid JSON Element.

**Standard JSON**

```json
{
  "database_host": "192.168.1.222"
}
```

**Prickle Property**

```json
{
  "database_host": {
    "value": "192.168.1.222"
  }
}
```

### Comments

The `//` key is used to define property comments. Comments should only be
used to provide additional context to the reader and should never
influence how the file is parsed or used by code. Comments can be a JSON
string or an array of JSON strings for multiline comments.

**Single Line Comment**

```json
{
  "database_host": {
    "//": "The IP address of the database to connect to.",
    "value": "192.168.1.0"
  }
}
```

**Multi-line Comment**

```json
{
  "database_host": {
    "//": [
      "The IP address of the database to connect to.",
      "The port can be suffixed using a colon.      "
    ],
    "value": "192.168.1.0"
  }
}
```

### Decorators

Decorators are named comments that convey a specific attribute of the
property to the reader. Decorators are declared using the comment key
followed by the name of the attribute. For example the `//default`
decorator might be used to denote the default value of the property.
Like comments, decorators should never influence how the file is parsed
or used by code.

Decorators and their meanings are entirely up to the author of the
config schema however the following decorators are commonly used
and are made available by this implementation of Prickle.

- `//default` - The default value for the property.
- `//reference` - Points the reader to another resource that can be used to learn more about the property, like a
  wiki/docs page.
- `//range` - The value must be within the given range.
- `//regex` - The value must match the regex pattern.
- `//empty-allowed` - The value can be empty.

## FaQ

### Why is this called Prickle?

Prickle is the collective noun for a group of hedgehogs. Hedgehogs are one
of my favourite animals and I think they are a fitting metaphor for config
files. For example, they both may seem intimidating at first but can be
nice once you get to know and understand them.

### Why not use an existing format?

Before working on Prickle I spent several months testing existing formats.
I found several that I liked but was disappointed with a lot of the Java
implementations. Every library that I tried had serious long-standing
bugs, was not being maintained, and lacked features that were important to
me. After weighing my options I realized JSON could easily meet all of my
criteria and the tools/libraries to work with JSON are widely available,
and extensively tested.

### Can I JiJ Prickle?

This library was not designed to be used with jar-in-jar tools. This use
case is not recommended or supported.

## Usage Guide

This library uses objects and annotations to define the schema of your
config file. If you are familiar with GSON, this process is very similar
to their annotation based serialization.

### Basic Example

Create a new class that represents your config object. Add fields to the
class for each property you want to be configurable. Only fields marked
with `@Value` will be included in the config. These fields can be common
java types like numbers, strings, booleans, and arrays. You can also use
custom types added by Minecraft like BlockPos and AttributeModifier.

**ExampleConfig.class**

```java
public class ExampleConfig {

    @Value
    public String host = "192.168.1.1";

    @Value
    public boolean logMessages = true;
}
```

**SomewhereInYourMod.class**

```java
    public void onModInit() {
    final ExampleConfig config = ConfigManager.load("example", new ExampleConfig());
    if (config.logMessages) {
        System.out.println("logging to " + config.host);
    }
}
```

**.minecraft/config/example.json**

```json
{
  "host": {
    "//default": "192.168.1.1",
    "value": "192.168.1.1"
  },
  "logMessages": {
    "//default": true,
    "value": true
  }
}
```

### Customizing Values

#### Value Properties

The `@Value` annotation includes a few properties that can help you define
how your property will be rendered in the config.

- `name` - Changes the name of the property in the JSON. By default, this is derived from the Java field name.
- `comment` - Adds a comment for the property. If the property is too long it will be wrapped.
- `reference` - Adds a link to an online reference where the reader can learn more. Like a wiki or docs site.
- `writeDefault` - Determines if the default value should also be included.

#### Ranged Numbers

Most numeric values can be limited to a given range by using a ranged
annotation. Currently `@RangedInt`, `@RangedFloat`, `@RangedDouble`, and
`@RangedLong` are supported. If one of these annotations is present the
field value will be validated when it is read and written.

- `min` - The lowest possible value.
- `max` - The highest possible value.

```json
{
  "ranged_int": {
    "//range": ">=0 AND <=100",
    "//default": 84,
    "value": 22
  }
}
```

#### Regex

String values can use `@Regex` to define a regex pattern that will be used
to validate the property value.

```json
{
  "logo_file": {
    "//regex": "^.*\\.(jpg|png)$",
    "//default": "logo.png",
    "value": "resources/my_logo.png"
  }
}
```

#### Array

Arrays and collections like List can use `@Array` to apply some validation
and formatting rules for the property.

- `inlineCount` - If the array has this many elements or less the writer will try to inline then when rendering the text
  to the config file.
- `inlineComplex` - By default only simple values like primitives will be inlined. This can override that behaviour, but
  it's not recommended.
- `allowEmpty` - Determines if the array can be configured to hold no elements. This is true by default.

```json
{
  "intArray": {
    "//": "An inlined array",
    "//default": [1,2,3,4],
    "value": [1,2,3,4]
  },
  "charArray": {
    "//": "Not inlined",
    "value": [
      "a",
      "b",
      "c",
      "d"
    ]
  },
  "stringList": {
    "//": "An array that must not be empty.",
    "//default": ["first","second","third"],
    "value": ["first","second","third"]
  }
}
```

### Custom Property Types

While this library has many built-in property types, you may want to use
your own custom types. This can be done by creating a new
`IPropertyAdapter` and adding it to your ConfigManager.Builder.

```java
    public void onModInit() {

    final ExampleConfig config = ConfigManager.load("test", new ExampleConfig(), builder -> {
        builder.adapter(new MyAdapter<>());
    });

    if (config.logMessages) {
        System.out.println("logging to " + config.host);
    }
}
```

You can also use an adapter for specific fields. This is done by marking
those fields with `@Adapter`. The class specified must have an accessible
constructor.

### Default Property Adapters

Some mods may want to add new default adapters for their custom types.
This can be especially useful if you are making a library that other
mods will reference in their config options. This can be done by using the
`IDefaultPropertyAdapters` service loader plugin. Simply create a new
class that implements this interface and add it to your
`META-INF/services/net.darkhax.pricklemc.common.api.config.property.IDefaultPropertyAdapters`
file.

**NOTE:** The default adapters are shared by all mods using this library.
You should only define default adapters for your own types or adapters
that require your own annotations to activate. If you want to add support
for a Minecraft type, add it to just your builder or reach out and work
with us to add it to the base library.

<!-- maven-start -->
## Build the 1.20.1 Fabric Port

This checkout includes the Gradle wrapper and the complete source needed to build the Fabric port. Run:

```gradle
./gradlew :fabric:build
```
<!-- maven-end -->

<!-- sponsor-start -->
## Sponsors

[![](https://assets.blamejared.com/nodecraft/darkhax.jpg)](https://nodecraft.com/r/darkhax)    
PrickleMC is sponsored by Nodecraft. Use code **[DARKHAX](https://nodecraft.com/r/darkhax)** for 30% of your first month of service!
<!-- sponsor-end -->

## Backport source and support

The complete source for this Fabric 1.20.1 backport is published in this repository. This is an independent community port and is not an official upstream release.

AI tools assisted with the 1.20.1 compatibility changes, tests, and this port documentation; this disclosure does not describe the upstream authors' work.
