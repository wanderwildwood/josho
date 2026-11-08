\version "2.24.0"
% A test that touches much of LilyPond: used to find which Guile modules it loads.
\include "rich-motif.ily"
\include "articulate.ly"
\header { title = "Test piece" composer = "Ada Whitlock" tagline = ##f }

swing = #(define-music-function (m) (ly:music?) #{ \tuplet 3/2 { $m } #})

melody = \relative c'' {
  \clef treble \key g \major \time 4/4 \tempo "Andante" 4 = 72
  \motif g4\p( a b c) | d2-> \swing { e8 d c } b4\f |
  \repeat volta 2 { a4.\trill g8 \grace { fis16 } e4 d | }
  \alternative { { c2 r | } { c2. r4 | } }
  \ottava #1 g'4 a b c \ottava #0 |
  << { d2 e } \\ { b2 c } >> |
  \override NoteHead.style = #'cross d4 e \revert NoteHead.style fis g |
  \transpose c d { c1 } \bar "|."
}
words = \lyricmode { Sing a | song of | morn -- ing, | here we | go a -- gain and | on }
harmonies = \chordmode { g1 | d:7 | a:m7 | e:m | c | d:sus4 | g | c | g }
bass = \relative c { \clef bass \key g \major g1 | fis | e | d | c | d | g, | c | g }
figs = \figuremode { <5 3>1 <6> <7> <6 4> <5> <4> <5> <6> <5> }

\score {
  <<
    \new ChordNames \harmonies
    \new Staff \new Voice = "m" \melody
    \new Lyrics \lyricsto "m" \words
    \new Staff \bass
    \new FiguredBass \figs
  >>
  \layout { }
  \midi { }
}
\markup \column {
  \line { \bold "Notes:" \italic "markup," \smallCaps "small caps," \circle 3 }
  \line { #(string-upcase "scheme strings") #(format #f "~a + ~a = ~a" 2 3 (+ 2 3)) }
}
#(let ((seen '()))
   (define (walk m)
     (hash-for-each
      (lambda (k sub)
        (if (not (memq sub seen))
            (begin
              (set! seen (cons sub seen))
              (let ((f (module-filename sub)))
                (if f (format (current-error-port) "GUILEMOD ~a\n" f)))
              (walk sub))))
      (module-submodules m)))
   (walk (resolve-module '() #f)))
