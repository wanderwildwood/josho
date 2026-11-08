\version "2.24.0"
% Mistakes on purpose: a Scheme error and a syntax error, so the error paths load too.
\relative c' { c4 d e f | g1 \undefinedthing }
#(display (car '()))
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
