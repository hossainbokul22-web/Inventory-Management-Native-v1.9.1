Proof that the native Daily PM numbers equal the web Daily PM numbers.
gen.py makes test data; web.js runs reference_index.html (the web page) in a browser on it;
PmModelCheck.java runs the app's PmModel on the same data. expected.out is the web output; CI fails if PmModel differs.
