# Memoisation Project
(copy paste the contents of this file on https://dillinger.io/ for a better read)

## Where should these files go?
All of these files should be located in the root directory of your project. 
```bash
mv ~/Downloads/memo-project-contents.zip ~/reng/
cd ~/reng
unzip memo-project-contents.zip
```

## Building project with Maven
Here we explain how to make your project work with the Maven build tool. Before you can think of building your project using maven, you should ensure that you have maven installed on your system. The commands below should work if you are running ubuntu, but if you are running a different linux distro or OSX then [this](https://maven.apache.org/install.html) page should hopefully help you get maven installed. 
```bash
sudo apt update
sudo apt install maven
```

Included is a bash script called `resolve.sh`, ensure that this script and the file `pom.xml` is in the root directory of your project, that is, `reng/`. If you have not altered the main directory layout of the [original code](https://github.com/marcin-chwedczuk/reng) (yes, you were allowed to add additional Java files and packages in `pl/marcinchwedczuk/reng/`), then the script will change the directory layout of your project and make it possible to build with maven.
```bash
cd ~/reng
./resolve.sh
```

If everything went according to plan you will see a successful build and 36 existing tests will run and pass. Afterwards you can delete the `resolve.sh` script. 
## BacktrackingMatcher API
The script `resolve.sh` also added two new Java files to your project, namely `MemoisationPolicy.java` and `MemoisationEncodingScheme.java`. Both these files should now be situated in `src/main/java/pl/marcinchwedczuk/reng/`. These two files define enums that can be used to specify the Policy and Encoding Scheme when performing memoisation during matching. 

In the file `BacktrackingMatcher.java` situated in `src/main/java/pl/marcinchwedczuk/reng/`, the following public method should be added:

```java
public static Match match(String s, RAst regex,
                          MemoisationPolicy memPolicy,
                          MemoisationEncodingScheme memEncScheme)
```

When performing matching via the BacktrackingMatcher you can now specify what policy (i.e., which nodes, RAst instances, should be memoised) and which encoding scheme (BitMap, Hash Table, Run-Length Encoding) should be used. The original match method without the `Continuation` as parameter can be implemented as follows:

```java
public static Match match(String s, RAst regex) {
    return match(s, regex, MemoisationPolicy.ALL, MemoisationEncodingScheme.BIT_MAP);
}
```

so that existing tests does not break the BacktrackingMatcher api. It does not matter what policy and encoding scheme you use as default, since when evaluating your engine, we will only be calling the `match` method using 4 parameters.

## Some Implementation Advice

* Some validation will be good, for example, when the lexer scans a backreference, if the number `n` in `\n` references an invalid group, then throw a `RParseException`. You can keep track of capturing groups in `RLexer` or `RParser`. For example, `(a)\3` should throw an `RParseException`. 
* Similarly, when scanning positive and negative lookaheads, if you have consumed a `(` and a `?` then if the next token it not a `=` or a `!` then throw an exception. 
* When selecting which nodes to memoise based on the memoisation policy, you can regard an instance of an `RAst` node as a `state` in the underlying finite-state machine. You can add fields to the `RAst` class that will help you with this. The `RAst` class can for example also have the following two fields: ```private int inDegree``` and ```private boolean isAncestorNode```. You can then use these to easily filter which nodes you need given the memoisation policy.
* These attributes `inDegree` and `isAncestorNode` for each `RAst` node can be populated either during parsing (the more difficult but efficient approach), or before you start matching in `BacktrackingMatcher` and you want to determine which nodes should be memoised. You can have a helper method to populate these fields and then afterwards you traverse all nodes and return a List of only the nodes you want. If you for example want to find all nodes that have an in-degree of greater than 1, then you can recursively traverse the RAst tree, when you encounter an `RAst` node `n1` of type `RAstType.ALTERNATIVE`, then the next node in the tree (will probably have to look at the parent of this node) after the alternation should have an in-degree equal to `n1.exprs.size()`. Determining ancestor nodes should be quite trivial, once you've thought about it a bit.
* Make sure normal matching with backreferences and lookaheads are working before you try to implement memoisation for normal regexes (without lookaheads or backreferences), then look at the memoisation policies and encoding schemes and then at the end you should try to add memoisation to lookaheads and backreferences. 
* If you are stuck with implementing the run-length encoding memoisation scheme and not sure how the binary tree comes into play, try to have a look at [this](http://stanford.edu/~abhijeet/papers/abhijeetIDEAS12.pdf) paper, you do not have to focus too much on the detail, but try to read and understand section 2.1, 2.2, 2.3, and 3. 

## Preliminary Marking Rubric
Your implementation will contribute to 40% (45% for rw345) of your project mark. About 35% of this mark will be based on how much of the requirements you implemented and whether it is working or not. The last marks will be an impression mark based on things such as quality of code and whether you added things like validation for certain edge cases (for example, see point 1 in the previous section) etc. If there are specific things you added in the implementation that you want us to look at please specify this clearly in your report or in a block comment in your `BacktrackingMatcher.java`.


