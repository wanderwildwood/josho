\version "2.24.0"
\header { title = "Bass model: up 2, down 3" tagline = ##f }
\paper { #(set-paper-size "a6") }
upper = \relative c'' { \clef treble \time 2/2 e2 f d e c d b c a b c1 \bar "|." }
lower = \relative c { \clef bass \time 2/2 c2 d b c a b g a f g c1 }
figs = \figuremode { s2 <6> s <6> s <6> s <6> s <6> s1 }
\score { \new PianoStaff << \new Staff \upper \new Staff \lower \new FiguredBass \figs >> \layout { } }
