package pl.marcinchwedczuk.reng.parser;

import java.util.ArrayList;
import java.util.List;

public class RLexer {
    private final String input;
    private int curr;
    private int capGroup = 0;
    public RLexer(String input) {
        this.input = input;
        this.curr = 0;
    }

    public List<RToken> split() {
        List<RToken> tokens = new ArrayList<>();

        while (curr < input.length()) {
            char c = input.charAt(curr);
            int cPos = curr;
            curr++;

            switch (c) {
                case '(':
                    tokens.add(new RToken(RTokenType.LPAREN, '(', cPos));
                    break;

                case ')':
                    capGroup++;
                    tokens.add(new RToken(RTokenType.RPAREN, ')', cPos));
                    break;

                case '[':
                    tokens.add(new RToken(RTokenType.LGROUP, '[', cPos));
                    break;

                case ']':
                    tokens.add(new RToken(RTokenType.RGROUP, ']', cPos));
                    break;

                case '{':
                    tokens.add(new RToken(RTokenType.LRANGE, '{',  cPos));
                    break;

                case '}':
                    tokens.add(new RToken(RTokenType.RRANGE, '}',  cPos));
                    break;

                case '*':
                    tokens.add(new RToken(RTokenType.STAR, '*', cPos));
                    break;

                case '+':
                    tokens.add(new RToken(RTokenType.PLUS, '+',  cPos));
                    break;

                case '?':
                    tokens.add(new RToken(RTokenType.QMARK, '?', cPos));
                    break;

                case '|':
                    tokens.add(new RToken(RTokenType.ALTERNATIVE, '|', cPos));
                    break;

                case '.':
                    tokens.add(new RToken(RTokenType.MATCH_ANY, '.', cPos));
                    break;

                case '^':
                    tokens.add(new RToken(RTokenType.AT_BEGINNING, '^', cPos));
                    break;

                case '$':
                    tokens.add(new RToken(RTokenType.AT_END, '$', cPos));
                    break;
                case '=':
                    tokens.add(new RToken(RTokenType.EQUAl, '=', cPos));
                    break;
                case '!':
                    tokens.add(new RToken(RTokenType.EXCLAMATION, '!', cPos));
                    break;
                case '\\':
                    // Escape sequence .e.g \$
                    if (curr > input.length())
                        throw new RParseException(cPos,
                                "Unexpected end of input inside escape sequence.");

                    char escape = input.charAt(curr);
                    curr++;


                    switch (escape) {
                        // Standard escapes

                        case 'n':
                            tokens.add(new RToken(RTokenType.CHARACTER, '\n', cPos));
                            break;

                        case 'r':
                            tokens.add(new RToken(RTokenType.CHARACTER, '\r', cPos));
                            break;

                        case '1':
                            if(1 > capGroup){throw new RParseException(cPos,
                                    "Group 1" + " does not exist!");
                            }
                            tokens.add(new RToken(RTokenType.BACKREF, '1', cPos));
                            break;

                        case '2':
                            if(2 > capGroup){throw new RParseException(cPos,
                                    "Group 2" + " does not exist!");
                            }
                            tokens.add(new RToken(RTokenType.BACKREF, '2', cPos));
                            break;

                        case '3':
                            if(3 > capGroup){throw new RParseException(cPos,
                                    "Group 3" + " does not exist!");
                            }
                            tokens.add(new RToken(RTokenType.BACKREF, '3', cPos));
                            break;

                        case '4':
                            if(4 > capGroup){throw new RParseException(cPos,
                                    "Group 4" + " does not exist!");
                            }
                            tokens.add(new RToken(RTokenType.BACKREF, '4', cPos));
                            break;

                        case '5':
                            if(5 > capGroup){throw new RParseException(cPos,
                                    "Group 5" + " does not exist!");
                            }
                            tokens.add(new RToken(RTokenType.BACKREF, '5', cPos));
                            break;

                        case '6':
                            if(6 > capGroup){throw new RParseException(cPos,
                                    "Group 6" + " does not exist!");
                            }
                            tokens.add(new RToken(RTokenType.BACKREF, '6', cPos));
                            break;

                        case '7':
                            if(7 > capGroup){throw new RParseException(cPos,
                                    "Group 7" + " does not exist!");
                            }
                            tokens.add(new RToken(RTokenType.BACKREF, '7', cPos));
                            break;

                        case '8':
                            if(8 > capGroup){throw new RParseException(cPos,
                                    "Group 8" + " does not exist!");
                            }
                            tokens.add(new RToken(RTokenType.BACKREF, '8', cPos));
                            break;

                        case '9':
                            if(9 > capGroup){throw new RParseException(cPos,
                                    "Group 9" + " does not exist!");
                            }
                            tokens.add(new RToken(RTokenType.BACKREF, '9', cPos));
                            break;

                        case '\\':
                            tokens.add(new RToken(RTokenType.CHARACTER, '\\', cPos));
                            break;

                        // Regex escapes
                        case '(': case ')':
                        case '[': case ']':
                        case '{': case '}':
                        case '*': case '+': case '?': case '|': case '.':
                        case '^': case '$':
                            tokens.add(new RToken(RTokenType.CHARACTER, escape, cPos));
                            break;

                        default:
                            throw new RParseException(cPos,
                                    "Unknown escape sequence: '" + escape + "'.");
                    }
                    break;

                default:
                    // Normal characters
                    tokens.add(new RToken(RTokenType.CHARACTER, c, cPos));
                    break;
            }
        }

        tokens.add(new RToken(RTokenType.EOF, '\0', input.length()));
        return tokens;
    }
}
