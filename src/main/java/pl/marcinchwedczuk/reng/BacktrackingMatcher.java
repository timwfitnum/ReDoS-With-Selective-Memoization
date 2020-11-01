package pl.marcinchwedczuk.reng;

import org.xml.sax.ErrorHandler;

import java.util.Hashtable;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@SuppressWarnings("SimplifiableConditionalExpression")
public class BacktrackingMatcher {
    private static int positioning = -1;

    private static Hashtable<String, Boolean> memoTable;
    private static MemoisationPolicy memoPolicy;
    private static MemoisationEncodingScheme memoScheme;
    private static BitMap bitMap;
    private static MemoisationPolicy pol;

    public static Match match(String s, RAst regex,
                              MemoisationPolicy memPolicy,
                              MemoisationEncodingScheme memEncScheme) {
        pol = memPolicy;
        memoScheme = memEncScheme;
        Input input = Input.of(s);
        positioning = -1;
        if (retPol() == MemoisationPolicy.IN_DEGREE_GREATER_THAN_1) {
            RAst.calcInDegree(regex);
        }

        if (pol == MemoisationPolicy.NONE) {
            while (true) {
                int startIndex = input.currentPos();
                AtomicInteger endIndex = new AtomicInteger(0);

                boolean hasMatch = match(input, regex, () -> {
                    endIndex.set(input.currentPos());
                    return true;
                });

                if (hasMatch) {
                    return new Match(s, hasMatch, startIndex, endIndex.get());
                }

                // We are at the end of the input - no match
                if (input.atEnd()) return new Match(s, hasMatch, -1, -1);

                // Try to match from next index
                positioning = input.currentPos();
                input.advance(1);
            }
        } else if (memoScheme == MemoisationEncodingScheme.BIT_MAP) {
            bitMap = new BitMap(numOfNodes(), input.length());
            while (true) {
                int startIndex = input.currentPos();
                AtomicInteger endIndex = new AtomicInteger(0);

                boolean hasMatch = match(input, regex, () -> {
                    endIndex.set(input.currentPos());
                    return true;
                });

                if (hasMatch) {
                    return new Match(s, hasMatch, startIndex, endIndex.get());
                }

                // We are at the end of the input - no match
                if (input.atEnd()) return new Match(s, hasMatch, -1, -1);

                // Try to match from next index
                positioning = input.currentPos();
                input.advance(1);
            }
        } else if (memoScheme == MemoisationEncodingScheme.HASH_TABLE) {
            memoTable = new Hashtable<String, Boolean>();
            while (true) {
                int startIndex = input.currentPos();
                AtomicInteger endIndex = new AtomicInteger(0);
                boolean hasMatch;

                if (!memoTable.containsKey(regex.getID() + " " + input.currentPos())) {
                    hasMatch = match(input, regex, () -> {
                        endIndex.set(input.currentPos());
                        return true;
                    });

                    memoTable.put(regex.getID() + " " + input.currentPos(), hasMatch);
                } else {
                    hasMatch = memoTable.get(regex.getID() + " " + input.currentPos());
                }

                if (hasMatch) {
                    memoTable.clear();
                    return new Match(s, hasMatch, startIndex, endIndex.get());
                }

                // We are at the end of the input - no match
                if (input.atEnd()) return new Match(s, hasMatch, -1, -1);

                // Try to match from next index
                positioning = input.currentPos();
                input.advance(1);
            }
        } else {
            return new Match(s, false, -1, -1);
        }
    }

    public static Match match(String s, RAst regex) {

        return match(s, regex, MemoisationPolicy.ALL
                ,MemoisationEncodingScheme.HASH_TABLE);
    }

    @SuppressWarnings("SimplifiableConditionalExpression")
    public static boolean match(Input input, RAst ast, Cont cont) {
        RAstType type = ast.type;
        InputPositionMarker m = null;

        switch (type) {
            case AT_BEGINNING:
                return input.atBeginning()
                        ? cont.run()
                        : false;

            case AT_END:
                return input.atEnd()
                        ? cont.run()
                        : false;

            case GROUP:
                if (input.atEnd()) return false;
                if (ast.chars.contains(input.current())) {
                    m = input.markPosition();
                    positioning = input.currentPos();
                    input.advance(1);
                    try {
                        return cont.run();
                    } finally {
                        input.restorePosition(m);
                    }
                }
                return false;

            case NEGATED_GROUP:
                if (input.atEnd()) return false;
                if (!ast.chars.contains(input.current())) {
                    m = input.markPosition();
                    positioning = input.currentPos();
                    input.advance(1);
                    try {
                        return cont.run();
                    } finally {
                        input.restorePosition(m);
                    }
                }
                return false;

            case CONCAT:
                return concatRec(input, ast.exprs, 0, cont);

            case ALTERNATIVE:
                return alternativeRec(input, ast.exprs, 0, cont);

            case REPEAT:
                return repeatRec(input, ast, 0, cont);

            case POSLOOKAHEAD:
                return posLookAhead(input, ast.exprs, 0, cont);

            case NEGLOOKAHEAD:
                if (negLookAhead(input, ast.exprs, 0, cont)) {
                    return cont.run();
                }
                return false;

            default:
                throw new AssertionError("Unknown enum value: " + type);
        }
    }

    private static boolean concatRec(Input input,
                                     List<RAst> exprs,
                                     int currExpr,
                                     Cont cont) {
        if (currExpr == exprs.size()) {
            return cont.run();
        }
        boolean match;

//        //Start Memoize for Bitmap
        if (pol != MemoisationPolicy.NONE) {
            if (memoScheme == MemoisationEncodingScheme.BIT_MAP) {
                if (pol == MemoisationPolicy.ALL) {
                    if (!bitMap.get(exprs.get(currExpr).getID(), input.currentPos()).isSet) {
                        match = match(input, exprs.get(currExpr), () ->
                                concatRec(input, exprs, currExpr + 1, cont));
                        bitMap.setValue(exprs.get(currExpr).getID(), input.currentPos(), match);
                        return match;
                    } else
                        return bitMap.get(exprs.get(currExpr).getID(), input.currentPos()).match;
                }

                //Memoize only in degree
                else if (pol == MemoisationPolicy.IN_DEGREE_GREATER_THAN_1) {
                    if (exprs.get(currExpr).getInDegree() > 1) {
                        if (!bitMap.get(exprs.get(currExpr).getID(), input.currentPos()).isSet) {
                            match = match(input, exprs.get(currExpr), () ->
                                    concatRec(input, exprs, currExpr + 1, cont));
                            bitMap.setValue(exprs.get(currExpr).getID(), input.currentPos(), match);
                            return match;
                        } else
                            return bitMap.get(exprs.get(currExpr).getID(), input.currentPos()).match;
                    } else {
                        return match(input, exprs.get(currExpr), () ->
//            // If it succeeded then match next expression
                                        concatRec(input, exprs, currExpr + 1, cont)
                        );
                    }
                }
//        //Memoize only ancestors
                else if (pol == MemoisationPolicy.ANCESTOR_NODES) {
                    if (exprs.get(currExpr).isAncestorNode()) {
                        if (!bitMap.get(exprs.get(currExpr).getID(), input.currentPos()).isSet) {
                            match = match(input, exprs.get(currExpr), () ->
                                    concatRec(input, exprs, currExpr + 1, cont));
                            bitMap.setValue(exprs.get(currExpr).getID(), input.currentPos(), match);
                            return match;
                        } else
                            return bitMap.get(exprs.get(currExpr).getID(), input.currentPos()).match;
                    } else {
                        return match(input, exprs.get(currExpr), () ->
                                // If it succeeded then match next expression
                                concatRec(input, exprs, currExpr + 1, cont)
                        );
                    }
                } else {
                    return match(input, exprs.get(currExpr), () ->
                            // If it succeeded then match next expression
                            concatRec(input, exprs, currExpr + 1, cont)
                    );
                }
            } else if (memoScheme == MemoisationEncodingScheme.HASH_TABLE) {
                // MEMOIZE ALL FOR HASHTABLE
                if (pol == MemoisationPolicy.ALL) {
                    if (!memoTable.containsKey(exprs.get(currExpr).getID() + " " + input.currentPos())) {
                        match = match(input, exprs.get(currExpr), () ->
                                concatRec(input, exprs, currExpr + 1, cont));
                        memoTable.put(exprs.get(currExpr).getID() + " " + input.currentPos(), match);
                        return match;
                    } else {
                        return memoTable.get(exprs.get(currExpr).getID() + " " + input.currentPos());
                    }
                }
                // MEMOIZE IN DEGREE FOR HASHTABLE
                else if (pol == MemoisationPolicy.IN_DEGREE_GREATER_THAN_1) {
                    if (exprs.get(currExpr).getInDegree() > 1) {
                        if (!memoTable.containsKey(exprs.get(currExpr).getID() + " " + input.currentPos())) {
                            match = match(input, exprs.get(currExpr), () ->
                                    concatRec(input, exprs, currExpr + 1, cont));
                            memoTable.put(exprs.get(currExpr).getID() + " " + input.currentPos(), match);
                            return match;
                        } else {
                            return memoTable.get(exprs.get(currExpr).getID() + " " + input.currentPos());
                        }
                    }
                    //DEGREE LESS SO DONT MEMOIZE
                    else {
                        return match(input, exprs.get(currExpr), () ->
//            // If it succeeded then match next expression
                                        concatRec(input, exprs, currExpr + 1, cont)
                        );
                    }
                }
                // MEMOIZE WITH ANCESTOR NODES
                else if (pol == MemoisationPolicy.ANCESTOR_NODES) {
                    if (exprs.get(currExpr).isAncestorNode()) {
                        if (!memoTable.containsKey(exprs.get(currExpr).getID() + " " + input.currentPos())) {
                            match = match(input, exprs.get(currExpr), () ->
                                    concatRec(input, exprs, currExpr + 1, cont));
                            memoTable.put(exprs.get(currExpr).getID() + " " + input.currentPos(), match);
                            return match;
                        } else {
                            return memoTable.get(exprs.get(currExpr).getID() + " " + input.currentPos());
                        }
                    } else {
                        return match(input, exprs.get(currExpr), () ->
//            // If it succeeded then match next expression
                                        concatRec(input, exprs, currExpr + 1, cont)
                        );
                    }
                } else {
                    return match(input, exprs.get(currExpr), () ->
//            // If it succeeded then match next expression
                                    concatRec(input, exprs, currExpr + 1, cont)
                    );
                }
            }
            // Match exprs.get(currExpr)
            //The normal code is here
            return match(input, exprs.get(currExpr), () ->
//            // If it succeeded then match next expression
                            concatRec(input, exprs, currExpr + 1, cont)
            );
        }
        // No memoizing
        else {
            return match(input, exprs.get(currExpr), () ->
                    concatRec(input, exprs, currExpr + 1, cont)
            );
        }
    }

    private static boolean repeatRec(Input input,
                                     RAst repeatAst,
                                     long matchCount,
                                     Cont cont) {
        if (matchCount > repeatAst.repeatMax)
            return false;
        boolean matched = false;
        if (pol != MemoisationPolicy.NONE) {
            //Start of memoize for BITMAP
            if (memoScheme == MemoisationEncodingScheme.BIT_MAP) {
                if (pol == MemoisationPolicy.ALL) {
                    if (input.currentPos() == positioning) {
                        if (!bitMap.get(repeatAst.headExpr().getID(), input.currentPos()).isSet) {
                            matched = match(input, repeatAst.headExpr(), cont);
                            bitMap.setValue(repeatAst.headExpr().getID(), input.currentPos(), matched);
                        } else {
                            matched = bitMap.get(repeatAst.headExpr().getID(), input.currentPos()).match;
                        }
                    } else {
                        if (!bitMap.get(repeatAst.headExpr().getID(), input.currentPos()).isSet) {
                            matched = match(input, repeatAst.headExpr(), () ->
                                    repeatRec(input, repeatAst, matchCount + 1, cont)
                            );
                            bitMap.setValue(repeatAst.headExpr().getID(), input.currentPos(), matched);

                        } else {
                            matched = bitMap.get(repeatAst.headExpr().getID(), input.currentPos()).match;
                        }
                        positioning = input.currentPos();
                    }
                }
//        //Memoize only in degree
                else if (pol == MemoisationPolicy.IN_DEGREE_GREATER_THAN_1) {
                    if (repeatAst.headExpr().getInDegree() > 1) {
                        if (input.currentPos() == positioning) {
                            if (!bitMap.get(repeatAst.headExpr().getID(), input.currentPos()).isSet) {
                                matched = match(input, repeatAst.headExpr(), cont);
                                bitMap.setValue(repeatAst.headExpr().getID(), input.currentPos(), matched);
                            } else {
                                matched = bitMap.get(repeatAst.headExpr().getID(), input.currentPos()).match;
                            }
                        } else {
                            if (!bitMap.get(repeatAst.headExpr().getID(), input.currentPos()).isSet) {
                                matched = match(input, repeatAst.headExpr(), () ->
                                        repeatRec(input, repeatAst, matchCount + 1, cont)
                                );
                                bitMap.setValue(repeatAst.headExpr().getID(), input.currentPos(), matched);

                            } else {
                                matched = bitMap.get(repeatAst.headExpr().getID(), input.currentPos()).match;
                            }
                            positioning = input.currentPos();
                        }
                    } else {
                        if (input.currentPos() == positioning) {
                            matched = match(input, repeatAst.headExpr(), cont);
                        } else {
                            matched = match(input, repeatAst.headExpr(), () ->
                                    repeatRec(input, repeatAst, matchCount + 1, cont)
                            );
                            positioning = input.currentPos();
                        }
                    }
                }
//        //Memoize only ancestors
                else if (pol == MemoisationPolicy.ANCESTOR_NODES) {
                    if (repeatAst.headExpr().isAncestorNode()) {
                        if (input.currentPos() == positioning) {
                            if (!bitMap.get(repeatAst.headExpr().getID(), input.currentPos()).isSet) {
                                matched = match(input, repeatAst.headExpr(), cont);
                                bitMap.setValue(repeatAst.headExpr().getID(), input.currentPos(), matched);
                            } else {
                                matched = bitMap.get(repeatAst.headExpr().getID(), input.currentPos()).match;
                            }
                        } else {
                            if (!bitMap.get(repeatAst.headExpr().getID(), input.currentPos()).isSet) {
                                matched = match(input, repeatAst.headExpr(), () ->
                                        repeatRec(input, repeatAst, matchCount + 1, cont)
                                );
                                bitMap.setValue(repeatAst.headExpr().getID(), input.currentPos(), matched);

                            } else {
                                matched = bitMap.get(repeatAst.headExpr().getID(), input.currentPos()).match;
                            }
                            positioning = input.currentPos();
                        }
                    } else {
                        if (input.currentPos() == positioning) {
                            matched = match(input, repeatAst.headExpr(), cont);
                        } else {
                            matched = match(input, repeatAst.headExpr(), () ->
                                    repeatRec(input, repeatAst, matchCount + 1, cont)
                            );
                            positioning = input.currentPos();
                        }
                    }
                }
            }
            // end of bitmap

            // START OF ALL MEMOIZATION FOR HASHTABLE
            else if (memoScheme == MemoisationEncodingScheme.HASH_TABLE) {
                if (pol == MemoisationPolicy.ALL) {
                    if (input.currentPos() == positioning) {
                        if (!memoTable.containsKey(repeatAst.headExpr().getID() + " " + input.currentPos())) {
                            matched = match(input, repeatAst.headExpr(), cont);
                            memoTable.put(repeatAst.headExpr().getID() + " " + input.currentPos(), matched);
                        } else {
                            matched = memoTable.get(repeatAst.headExpr().getID() + " " + input.currentPos());
                        }
                    } else {
                        if (!memoTable.containsKey(repeatAst.headExpr().getID() + " " + input.currentPos())) {
                            matched = match(input, repeatAst.headExpr(), () ->
                                    repeatRec(input, repeatAst, matchCount + 1, cont)
                            );
                            positioning = input.currentPos();
                            memoTable.put(repeatAst.headExpr().getID() + " " + input.currentPos(), matched);
                        } else {
                            matched = memoTable.get(repeatAst.headExpr().getID() + " " + input.currentPos());
                        }

                    }
                }
                // START OF IN DEGREE MEMOIZATION FOR HASHTABLE
                else if (pol == MemoisationPolicy.IN_DEGREE_GREATER_THAN_1) {
                    if (repeatAst.headExpr().getInDegree() > 1) {
                        if (input.currentPos() == positioning) {
                            if (!memoTable.containsKey(repeatAst.headExpr().getID() + " " + input.currentPos())) {
                                matched = match(input, repeatAst.headExpr(), cont);
                                memoTable.put(repeatAst.headExpr().getID() + " " + input.currentPos(), matched);
                            } else {
                                matched = memoTable.get(repeatAst.headExpr().getID() + " " + input.currentPos());
                            }
                        } else {
                            if (!memoTable.containsKey(repeatAst.headExpr().getID() + " " + input.currentPos())) {
                                matched = match(input, repeatAst.headExpr(), () ->
                                        repeatRec(input, repeatAst, matchCount + 1, cont)
                                );
                                positioning = input.currentPos();
                                memoTable.put(repeatAst.headExpr().getID() + " " + input.currentPos(), matched);
                            } else {
                                matched = memoTable.get(repeatAst.headExpr().getID() + " " + input.currentPos());
                            }

                        }
                    }
                    // IF NOT IN DEGREE DONT MEMOIZE SO ACT NORMAL
                    else {
                        if (input.currentPos() == positioning) {
                            matched = match(input, repeatAst.headExpr(), cont);
                        } else {
                            matched = match(input, repeatAst.headExpr(), () ->
                                    repeatRec(input, repeatAst, matchCount + 1, cont)
                            );
                            positioning = input.currentPos();
                        }
                    }
                }
                //MEMOIZE ANCESTOR NODES IN HASHTABLE
                else if (pol == MemoisationPolicy.ANCESTOR_NODES) {
                    if (repeatAst.headExpr().isAncestorNode()) {
                        if (input.currentPos() == positioning) {
                            if (!memoTable.containsKey(repeatAst.headExpr().getID() + " " + input.currentPos())) {
                                matched = match(input, repeatAst.headExpr(), cont);
                                memoTable.put(repeatAst.headExpr().getID() + " " + input.currentPos(), matched);
                            } else {
                                matched = memoTable.get(repeatAst.headExpr().getID() + " " + input.currentPos());
                            }
                        } else {
                            if (!memoTable.containsKey(repeatAst.headExpr().getID() + " " + input.currentPos())) {
                                matched = match(input, repeatAst.headExpr(), () ->
                                        repeatRec(input, repeatAst, matchCount + 1, cont)
                                );

                                memoTable.put(repeatAst.headExpr().getID() + " " + input.currentPos(), matched);
                            } else {
                                matched = memoTable.get(repeatAst.headExpr().getID() + " " + input.currentPos());
                            }
                            positioning = input.currentPos();
                        }
                    }
                    // IF NOT ANCESTOR DONT MEMOIZE SO ACT NORMAL
                    else {
                        if (input.currentPos() == positioning) {
                            matched = match(input, repeatAst.headExpr(), cont);
                        } else {
                            matched = match(input, repeatAst.headExpr(), () ->
                                    repeatRec(input, repeatAst, matchCount + 1, cont)
                            );
                            positioning = input.currentPos();
                        }
                    }
                }
                // NO MEMOIZING AT ALL
            } else {
                if (input.currentPos() == positioning) {
                    matched = match(input, repeatAst.headExpr(), cont);
                } else {

                    matched = match(input, repeatAst.headExpr(), () ->
                            repeatRec(input, repeatAst, matchCount + 1, cont)
                    );
                    positioning = input.currentPos();
                }
            }
        } else {
            // The normal code goes here
            if (input.currentPos() == positioning) {
                matched = match(input, repeatAst.headExpr(), cont);
            } else {

                matched = match(input, repeatAst.headExpr(), () ->
                        repeatRec(input, repeatAst, matchCount + 1, cont)
                );
                positioning = input.currentPos();
            }

        }
        if (!matched && (matchCount >= repeatAst.repeatMin)) {
            // r{N} did not match.
            // Here we are matching r{N-1}, we are sure it is matching
            // because this function was called.
            return cont.run();
        }
        return matched;
        // r{N} matched?

    }

    private static boolean alternativeRec(Input input,
                                          List<RAst> expr,
                                          int currExpr,
                                          Cont cont) {
        if (currExpr == expr.size()) {
            // We tried all alternatives but achieved no match.
            return false;
        }
        boolean matched;
        if (pol != MemoisationPolicy.NONE) {
//        //Start Memoize for Bitmap
            if (memoScheme == MemoisationEncodingScheme.BIT_MAP) {
                if (pol == MemoisationPolicy.ALL) {
                    if (!bitMap.get(expr.get(currExpr).getID(), input.currentPos()).isSet) {
                        matched = match(input, expr.get(currExpr), cont);
                        bitMap.setValue(expr.get(currExpr).getID(), input.currentPos(), matched);
                    } else {
                        matched = bitMap.get(expr.get(currExpr).getID(), input.currentPos()).match;
                    }
                }
//        Memoize only in degree
                else if (pol == MemoisationPolicy.IN_DEGREE_GREATER_THAN_1) {
                    if (expr.get(currExpr).getInDegree() > 1) {
                        if (!bitMap.get(expr.get(currExpr).getID(), input.currentPos()).isSet) {
                            matched = match(input, expr.get(currExpr), cont);
                            bitMap.setValue(expr.get(currExpr).getID(), input.currentPos(), matched);
                        } else {
                            matched = bitMap.get(expr.get(currExpr).getID(), input.currentPos()).match;
                        }
                    } else {
                        matched = match(input, expr.get(currExpr), cont);
                    }
                }
                //Memoize only ancestors
                else if (pol == MemoisationPolicy.ANCESTOR_NODES) {
                    if (expr.get(currExpr).isAncestorNode()) {
                        if (!bitMap.get(expr.get(currExpr).getID(), input.currentPos()).isSet) {
                            matched = match(input, expr.get(currExpr), cont);
                            bitMap.setValue(expr.get(currExpr).getID(), input.currentPos(), matched);
                        } else {
                            matched = bitMap.get(expr.get(currExpr).getID(), input.currentPos()).match;
                        }
                    } else {
                        matched = match(input, expr.get(currExpr), cont);
                    }
                } else {
                    // Let's try next alternative "branch"
                    // The normal code goes here
                    matched = match(input, expr.get(currExpr), cont);
                }
            } else if (memoScheme == MemoisationEncodingScheme.HASH_TABLE) {
                //Memoize for All and Hashtable
                if (pol == MemoisationPolicy.ALL) {
                    if (!memoTable.containsKey(expr.get(currExpr).getID() + " " + input.currentPos())) {
                        matched = match(input, expr.get(currExpr), cont);
                        memoTable.put(expr.get(currExpr).getID() + " " + input.currentPos(), matched);
                    } else {
                        matched = memoTable.get(expr.get(currExpr).getID() + " " + input.currentPos());
                    }
                }
                //Memoize for in degree and hashtable
                else if (pol == MemoisationPolicy.IN_DEGREE_GREATER_THAN_1) {
                    if (expr.get(currExpr).getInDegree() > 1) {
                        if (!memoTable.containsKey(expr.get(currExpr).getID() + " " + input.currentPos())) {
                            matched = match(input, expr.get(currExpr), cont);
                            memoTable.put(expr.get(currExpr).getID() + " " + input.currentPos(), matched);
                        } else {
                            matched = memoTable.get(expr.get(currExpr).getID() + " " + input.currentPos());
                        }
                    } else {
                        matched = match(input, expr.get(currExpr), cont);
                    }
                }
                // Memoize for ancestor and hashtable
                else if (pol == MemoisationPolicy.ANCESTOR_NODES) {
                    if (expr.get(currExpr).isAncestorNode()) {
                        if (!memoTable.containsKey(expr.get(currExpr).getID() + " " + input.currentPos())) {
                            matched = match(input, expr.get(currExpr), cont);
                            memoTable.put(expr.get(currExpr).getID() + " " + input.currentPos(), matched);
                        } else {
                            matched = memoTable.get(expr.get(currExpr).getID() + " " + input.currentPos());
                        }
                    } else {
                        matched = match(input, expr.get(currExpr), cont);
                    }
                } else {
                    // Let's try next alternative "branch"
                    // The normal code goes here
                    matched = match(input, expr.get(currExpr), cont);
                }
            } else {
                // Let's try next alternative "branch"
                // The normal code goes here
                matched = match(input, expr.get(currExpr), cont);
            }
        } else {
            // Let's try next alternative "branch"
            // The normal code goes here
            matched = match(input, expr.get(currExpr), cont);
        }

        if (matched) return true;
        // Let's try next alternative "branch"
        return alternativeRec(input, expr, currExpr + 1, cont);
    }

    private static boolean posLookAhead(Input input,
                                        List<RAst> expr,
                                        int currExpr,
                                        Cont cont) {
        if (currExpr == expr.size()) {
            // We tried all attempts and achieved a match.
            return true;
        }
        boolean matched;

        //Start Memoize for Bitmap
        if (pol != MemoisationPolicy.NONE) {
            if (memoScheme == MemoisationEncodingScheme.BIT_MAP) {
                if (pol == MemoisationPolicy.ALL) {
                    if (!bitMap.get(expr.get(currExpr).getID(), input.currentPos()).isSet) {
                        matched = match(input, expr.get(currExpr), cont);
                        bitMap.setValue(expr.get(currExpr).getID(), input.currentPos(), matched);
                    } else {
                        matched = bitMap.get(expr.get(currExpr).getID(), input.currentPos()).match;
                    }
                }
//        //Memoize only in degree
                else if (pol == MemoisationPolicy.IN_DEGREE_GREATER_THAN_1) {
                    if (expr.get(currExpr).getInDegree() > 1) {
                        if (!bitMap.get(expr.get(currExpr).getID(), input.currentPos()).isSet) {
                            matched = match(input, expr.get(currExpr), cont);
                            bitMap.setValue(expr.get(currExpr).getID(), input.currentPos(), matched);
                        } else {
                            matched = bitMap.get(expr.get(currExpr).getID(), input.currentPos()).match;
                        }
                    } else {
                        matched = match(input, expr.get(currExpr), cont);
                    }
                }
//        //Memoize only ancestors
                else if (pol == MemoisationPolicy.ANCESTOR_NODES) {
                    if (expr.get(currExpr).isAncestorNode()) {
                        if (!bitMap.get(expr.get(currExpr).getID(), input.currentPos()).isSet) {
                            matched = match(input, expr.get(currExpr), cont);
                            bitMap.setValue(expr.get(currExpr).getID(), input.currentPos(), matched);
                        } else {
                            matched = bitMap.get(expr.get(currExpr).getID(), input.currentPos()).match;
                        }
                    } else {
                        matched = match(input, expr.get(currExpr), cont);
                    }
                } else {
                    matched = match(input, expr.get(currExpr), cont);
                }
            } else if (memoScheme == MemoisationEncodingScheme.HASH_TABLE) {
                //Memoize all
                if (pol == MemoisationPolicy.ALL) {
                    if (!memoTable.containsKey(expr.get(currExpr).getID() + " " + input.currentPos())) {
                        matched = match(input, expr.get(currExpr), cont);
                        memoTable.put(expr.get(currExpr).getID() + " " + input.currentPos(), matched);
                    } else {
                        matched = memoTable.get(expr.get(currExpr).getID() + " " + input.currentPos());
                    }
                }
                //Memoize for in degree
                else if (pol == MemoisationPolicy.IN_DEGREE_GREATER_THAN_1) {
                    if (expr.get(currExpr).getInDegree() > 1) {
                        if (!memoTable.containsKey(expr.get(currExpr).getID() + " " + input.currentPos())) {
                            matched = match(input, expr.get(currExpr), cont);
                            memoTable.put(expr.get(currExpr).getID() + " " + input.currentPos(), matched);
                        } else {
                            matched = memoTable.get(expr.get(currExpr).getID() + " " + input.currentPos());
                        }
                    } else {
                        matched = match(input, expr.get(currExpr), cont);
                    }
                }
                //Memoize for ancestors
                else if (pol == MemoisationPolicy.ANCESTOR_NODES) {
                    if (expr.get(currExpr).isAncestorNode()) {
                        if (!memoTable.containsKey(expr.get(currExpr).getID() + " " + input.currentPos())) {
                            matched = match(input, expr.get(currExpr), cont);
                            memoTable.put(expr.get(currExpr).getID() + " " + input.currentPos(), matched);
                        } else {
                            matched = memoTable.get(expr.get(currExpr).getID() + " " + input.currentPos());
                        }
                    } else {
                        matched = match(input, expr.get(currExpr), cont);
                    }
                } else {
                    matched = match(input, expr.get(currExpr), cont);
                }
            } else {
                matched = match(input, expr.get(currExpr), cont);
            }
        }
        // Memoize for none
        else {
            //Normal code goes here
            matched = match(input, expr.get(currExpr), cont);
        }
        if (matched) return posLookAhead(input, expr, currExpr + 1, cont);
        else {
            return false;
        }
    }

    private static boolean negLookAhead(Input input,
                                        List<RAst> expr,
                                        int currExpr,
                                        Cont cont) {

        if (currExpr == expr.size()) {
            // We tried all attempts and achieved a match.
            return true;
        }
        boolean matched;
        //Start Memoize for Bitmap
        if (pol != MemoisationPolicy.NONE) {
            if (memoScheme == MemoisationEncodingScheme.BIT_MAP) {
                if (pol == MemoisationPolicy.ALL) {
                    if (!bitMap.get(expr.get(currExpr).getID(), input.currentPos()).isSet) {
                        matched = match(input, expr.get(currExpr), cont);
                        bitMap.setValue(expr.get(currExpr).getID(), input.currentPos(), matched);
                    } else {
                        matched = bitMap.get(expr.get(currExpr).getID(), input.currentPos()).match;
                    }
                    if (!matched) return negLookAhead(input, expr, currExpr + 1, cont);
                    else {
                        return false;
                    }
                }
//        //Memoize only in degree
                else if (pol == MemoisationPolicy.IN_DEGREE_GREATER_THAN_1) {
                    if (expr.get(currExpr).getInDegree() > 1) {
                        if (!bitMap.get(expr.get(currExpr).getID(), input.currentPos()).isSet) {
                            matched = match(input, expr.get(currExpr), cont);
                            bitMap.setValue(expr.get(currExpr).getID(), input.currentPos(), matched);
                        } else {
                            matched = bitMap.get(expr.get(currExpr).getID(), input.currentPos()).match;
                        }
                    } else {
                        matched = match(input, expr.get(currExpr), cont);
                    }
                    if (!matched) return negLookAhead(input, expr, currExpr + 1, cont);
                    else {
                        return false;
                    }
                }
//        //Memoize only ancestors
                else if (pol == MemoisationPolicy.ANCESTOR_NODES) {
                    if (expr.get(currExpr).isAncestorNode()) {
                        if (!bitMap.get(expr.get(currExpr).getID(), input.currentPos()).isSet) {
                            matched = match(input, expr.get(currExpr), cont);
                            bitMap.setValue(expr.get(currExpr).getID(), input.currentPos(), matched);
                        } else {
                            matched = bitMap.get(expr.get(currExpr).getID(), input.currentPos()).match;
                        }
                    } else {
                        matched = match(input, expr.get(currExpr), cont);
                    }
                    if (!matched) return negLookAhead(input, expr, currExpr + 1, cont);
                    else {
                        return false;
                    }
                }
                else {
                    matched = match(input, expr.get(currExpr), cont);
                }
            } else if (memoScheme == MemoisationEncodingScheme.HASH_TABLE) {
                //Start of Memoization for Hashtable
                if (pol == MemoisationPolicy.ALL) {

                    if (!memoTable.containsKey(expr.get(currExpr).getID() + " " + input.currentPos())) {
                        matched = match(input, expr.get(currExpr), cont);
                        memoTable.put(expr.get(currExpr).getID() + " " + input.currentPos(), matched);
                    } else {
                        matched = memoTable.get(expr.get(currExpr).getID() + " " + input.currentPos());
                    }
                    if (matched) return negLookAhead(input, expr, currExpr + 1, cont);
                    else {
                        return false;
                    }
                }
                //Memoize in degree
                else if (pol == MemoisationPolicy.IN_DEGREE_GREATER_THAN_1) {
                    if (expr.get(currExpr).getInDegree() > 1) {
                        if (!memoTable.containsKey(expr.get(currExpr).getID() + " " + input.currentPos())) {
                            matched = match(input, expr.get(currExpr), cont);
                            memoTable.put(expr.get(currExpr).getID() + " " + input.currentPos(), matched);
                        } else {
                            matched = memoTable.get(expr.get(currExpr).getID() + " " + input.currentPos());
                        }
                        if (matched) return negLookAhead(input, expr, currExpr + 1, cont);
                        else {
                            return false;
                        }
                    } else {

                        matched = match(input, expr.get(currExpr), cont);
                        if (!matched) return negLookAhead(input, expr, currExpr + 1, cont);
                        else {
                            return false;
                        }
                    }
                }
                //Memoize ancestors
                else if (pol == MemoisationPolicy.ANCESTOR_NODES) {
                    if (expr.get(currExpr).getInDegree() > 1) {
                        if (!memoTable.containsKey(expr.get(currExpr).getID() + " " + input.currentPos())) {
                            matched = match(input, expr.get(currExpr), cont);
                            memoTable.put(expr.get(currExpr).getID() + " " + input.currentPos(), matched);
                        } else {
                            matched = memoTable.get(expr.get(currExpr).getID() + " " + input.currentPos());
                        }
                        if (matched) return negLookAhead(input, expr, currExpr + 1, cont);
                        else {
                            return false;
                        }
                    } else {

                        matched = match(input, expr.get(currExpr), cont);
                        if (!matched) return negLookAhead(input, expr, currExpr + 1, cont);
                        else {
                            return false;
                        }
                    }
                } else {
                    matched = match(input, expr.get(currExpr), cont);
                }
            }
            else {
                matched = match(input, expr.get(currExpr), cont);
            }
        } else {

            matched = match(input, expr.get(currExpr), cont);
        }
        if (!matched) return negLookAhead(input, expr, currExpr + 1, cont);
        else {
            return false;
        }
    }

    public static MemoisationPolicy retPol() {
        return memoPolicy;
    }

    public static MemoisationEncodingScheme retScheme() {
        return memoScheme;
    }

    public static int numOfNodes() {
        return RAst.nodes;
    }

    // Classes for bitmap memoization
    static class BitMap {
        // set as new entry for single memoize point
        private BitMapEntry[][] bitMap = new BitMapEntry[0][0];

        public BitMap(){

        }
        // create bitMap
        public BitMap(int regNodes, int inputLength){
            // + 1 to stop out of bounds errors
            this.bitMap = new BitMapEntry[regNodes + 1][inputLength + 1];
            for(int i = 0; i < bitMap.length; i++){
                for(int j = 0; j < bitMap[i].length; j++){
                    bitMap[i][j] = new BitMapEntry();
                }
            }
        }
        // return specific bitMapEntry
        public BitMapEntry get(int node, int stringPos){
            return bitMap[node][stringPos];
        }
        // set value of bitmap entry at positon [node][stringPos]
        public void setValue(int node, int stringPos, boolean match){
            if(!this.bitMap[node][stringPos].isSet)
                this.bitMap[node][stringPos].match = match;
            this.bitMap[node][stringPos].isSet = true;
        }

    }
    static class BitMapEntry {
        public boolean match;
        public boolean isSet = false;
    }

}

