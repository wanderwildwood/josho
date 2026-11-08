\version "2.24.0"
% Uses a Guile module whose compiled .go the app does not ship: it must still load (interpreted).
#(use-modules (web uri) (srfi srfi-19))
\markup #(uri->string (string->uri "https://lilypond.org/doc"))
\relative c' { c4 d e f | g1 }
