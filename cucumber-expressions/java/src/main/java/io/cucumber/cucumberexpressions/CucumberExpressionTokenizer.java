package io.cucumber.cucumberexpressions;

import io.cucumber.cucumberexpressions.Ast.Token;
import io.cucumber.cucumberexpressions.Ast.Token.Type;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.PrimitiveIterator.OfInt;

import static io.cucumber.cucumberexpressions.CucumberExpressionException.createCantEscape;
import static io.cucumber.cucumberexpressions.CucumberExpressionException.createTheEndOfLineCanNotBeEscapedException;

final class CucumberExpressionTokenizer {

    List<Token> tokenize(String expression) {
        List<Token> tokens = new ArrayList<>();
        tokenizeImpl(expression).forEach(tokens::add);
        return tokens;
    }

    private Iterable<Token> tokenizeImpl(String expression) {
        return () -> new TokenIterator(expression);

    }

    private static class TokenIterator implements Iterator<Token> {
        final OfInt codePoints;
        private final String expression;
        StringBuilder buffer;
        Type previousTokenType;
        Type currentTokenType;
        boolean treatAsText;
        int index;
        int escaped;

        TokenIterator(String expression) {
            this.expression = expression;
            codePoints = expression.codePoints().iterator();
            buffer = new StringBuilder();
            previousTokenType = null;
            currentTokenType = Type.START_OF_LINE;
            treatAsText = false;
            index = 0;
            escaped = 0;
        }

        @Override
        public boolean hasNext() {
            return previousTokenType != Type.END_OF_LINE;
        }

        @Override
        public Token next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            if (currentTokenType == Type.START_OF_LINE) {
                Token token = convertBufferToToken(currentTokenType);
                advanceTokenTypes();
                return token;
            }

            while (codePoints.hasNext()) {
                int token = codePoints.nextInt();
                if (!treatAsText && token == Ast.ESCAPE_CHARACTER) {
                    escaped++;
                    treatAsText = true;
                    continue;
                }
                currentTokenType = tokenTypeOf(token, treatAsText);
                treatAsText = false;

                if (previousTokenType != Type.START_OF_LINE
                        && (currentTokenType != previousTokenType
                        || (currentTokenType != Type.WHITE_SPACE && currentTokenType != Type.TEXT))) {
                    Token t = convertBufferToToken(previousTokenType);
                    advanceTokenTypes();
                    buffer.appendCodePoint(token);
                    return t;
                } else {
                    advanceTokenTypes();
                    buffer.appendCodePoint(token);
                }
            }

            if (buffer.length() > 0) {
                Token token = convertBufferToToken(previousTokenType);
                advanceTokenTypes();
                return token;
            }

            currentTokenType = Type.END_OF_LINE;
            if (treatAsText) {
                throw createTheEndOfLineCanNotBeEscapedException(expression);
            }
            Token token = convertBufferToToken(currentTokenType);
            advanceTokenTypes();
            return token;
        }

        private void advanceTokenTypes() {
            previousTokenType = currentTokenType;
            currentTokenType = null;
        }

        private Token convertBufferToToken(Type currentTokenType) {
            int escapeTokens = 0;
            if (currentTokenType == Type.TEXT) {
                escapeTokens = escaped;
                escaped = 0;
            }
            int endIndex = index + buffer.codePointCount(0, buffer.length()) + escapeTokens;
            Token t = new Token(buffer.toString(), currentTokenType, index, endIndex);
            buffer = new StringBuilder();
            this.index = endIndex;
            return t;
        }

        private Type tokenTypeOf(Integer token, boolean treatAsText) {
            return treatAsText ? textTokenTypeOf(token) : tokenTypeOf(token);
        }

        private Type textTokenTypeOf(Integer token) {
            if (Character.isWhitespace(token)) {
                return Type.TEXT;
            }
            switch (token) {
                case (int) Ast.ESCAPE_CHARACTER:
                case (int) Ast.ALTERNATION_CHARACTER:
                case (int) Ast.BEGIN_PARAMETER_CHARACTER:
                case (int) Ast.END_PARAMETER_CHARACTER:
                case (int) Ast.BEGIN_OPTIONAL_CHARACTER:
                case (int) Ast.END_OPTIONAL_CHARACTER:
                    return Type.TEXT;
            }
            throw createCantEscape(expression, index + escaped);
        }

        private Type tokenTypeOf(Integer token) {
            if (Character.isWhitespace(token)) {
                return Type.WHITE_SPACE;
            }
            switch (token) {
                case (int) Ast.ALTERNATION_CHARACTER:
                    return Type.ALTERNATION;
                case (int) Ast.BEGIN_PARAMETER_CHARACTER:
                    return Type.BEGIN_PARAMETER;
                case (int) Ast.END_PARAMETER_CHARACTER:
                    return Type.END_PARAMETER;
                case (int) Ast.BEGIN_OPTIONAL_CHARACTER:
                    return Type.BEGIN_OPTIONAL;
                case (int) Ast.END_OPTIONAL_CHARACTER:
                    return Type.END_OPTIONAL;
            }
            return Type.TEXT;
        }

    }

}
