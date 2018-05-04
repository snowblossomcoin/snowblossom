
# Exceptions

If something should only be due to:
* programmer error
* major environment problem (not having a crypto algorithm)
* LOCAL database corruption
* LOCAL misconfiguration

Then it may be a runtime exception

If it is due to something outside, then it should absolutely not be a runtime exception.


