/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

grammar DataPrepperExpression;

@header {
    package org.opensearch.dataprepper.expression.antlr;
}

fragment
Digit
    : '0'
    | NonZeroDigit
    ;

fragment
NonZeroDigit
    : [1-9]
    ;

Integer
    : '0'
    | NonZeroDigit Digit*
    ;

Float
    : NonZeroDigit? Digit '.' Digit
    | NonZeroDigit? Digit '.' Digit+ NonZeroDigit
    | '.' Digit
    | '.' Digit* NonZeroDigit
    ;

Boolean
    : TRUE
    | FALSE
    ;

fragment
StringCharacters
    : StringCharacter+
    ;

fragment
StringCharacter
    : ~["\\$]
    | EscapeSequence
    ;

fragment
EscapeSequence
    : '\\' [btnfr"'\\$]
    ;

fragment
JsonPointerEscapeSequence
    : '\\' [btnfr"'\\/]
    ;

fragment
JsonPointerStringCharacters
    : JsonPointerStringCharacter+
    ;

fragment
JsonPointerStringCharacter
    : ~["\\]
    | JsonPointerEscapeSequence
    ;

fragment
JsonPointerCharacter
    : [A-Za-z0-9_]
    ;

fragment
JsonPointerCharacters
    : JsonPointerCharacter+
    ;

JsonPointer
    : FORWARDSLASH JsonPointerCharacters? (FORWARDSLASH JsonPointerCharacters)*
    ;

EscapedJsonPointer
    : DOUBLEQUOTE FORWARDSLASH JsonPointerStringCharacters? DOUBLEQUOTE
    ;

String
    : DOUBLEQUOTE StringCharacters? DOUBLEQUOTE
    ;

fragment
VariableNameLeadingCharacter
    : [A-Za-z_]
    ;

fragment
VariableNameCharacter
    : VariableNameLeadingCharacter
    | [0-9-]
    ;

fragment
VariableNameCharacters
    : VariableNameLeadingCharacter VariableNameCharacter*
    ;

VariableIdentifier
    : '${' VariableNameCharacters '}'
    ;

expression
    : binaryOperatorExpression EOF
    | OTHER {System.err.println("unknown char: " + $OTHER.text);}
    ;

binaryOperatorExpression
    : conditionalExpression
    ;

conditionalExpression
    : conditionalExpression conditionalOperator equalityOperatorExpression
    | equalityOperatorExpression
    ;

equalityOperatorExpression
    : equalityOperatorExpression binaryOperator regexOperatorExpression
    | regexOperatorExpression
    ;

regexOperatorExpression
    : regexOperatorExpression regexEqualityOperator regexPattern
    | relationalOperatorExpression
    ;

relationalOperatorExpression
    : relationalOperatorExpression relationalOperator setOperatorExpression
    | setOperatorExpression
    ;

setOperatorExpression
    : setOperatorExpression setOperator setInitializer
    | unaryOperatorExpression
    ;

unaryOperatorExpression
    : primary
    | setInitializer
    | regexPattern
    | parenthesisExpression
    | unaryNotOperatorExpression
    ;

unaryNotOperatorExpression
    : unaryOperator primary
    ;

binaryOperator
    : relationalOperator
    | equalityOperator
    ;

regexEqualityOperator
    : '=~'
    | '!~'
    ;

setOperator
    : 'in'
    | 'not in'
    ;


conditionalOperator
    : 'and'
    | 'or'
    ;

unaryOperator
    : 'not'
    ;

equalityOperator
    : '=='
    | '!='
    ;

relationalOperator
    : '<'
    | '<='
    | '>'
    | '>='
    ;

primary
    : literal
    | jsonPointer
    | variableIdentifier
    | setInitializer
    ;

jsonPointer
    : JsonPointer
    | EscapedJsonPointer
    ;

regexPattern
    : jsonPointer
    | String
    ;

parenthesisExpression
    : '(' binaryOperatorExpression ')'
    ;

setInitializer
    : '{' primary (',' primary)* '}'
    ;

variableIdentifier
    : variableName
    ;

variableName
    : VariableIdentifier
    ;

literal
    : Float
    | Integer
    | Boolean
    | String
    ;

EQUAL : '==';
NOT_EQUAL : '!=';
LT : '<';
GT : '>';
LTE : '<=';
GTE : '>=';
MATCH_REGEX_PATTERN : '=~';
NOT_MATCH_REGEX_PATTERN : '!~';
IN_SET : 'in';
NOT_IN_SET : 'not in';
AND : 'and';
OR : 'or';
NOT : 'not';
LPAREN : '(';
RPAREN : ')';
LBRACK : '[';
RBRACK : ']';
LBRACE : '{';
RBRACE : '}';
TRUE : 'true';
FALSE : 'false';
FORWARDSLASH : '/';
DOUBLEQUOTE : '"';
SET_SEPARATOR : ',';
PERIOD : '.';

SPACE
    : [ \t\r\n] -> skip
    ;
