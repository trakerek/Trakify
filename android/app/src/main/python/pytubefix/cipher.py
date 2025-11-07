"""
Cipher module for PytubeFix (Python-only version, no Node.js required).
It interprets the signature functions directly in Python.
"""
import logging
import re

from pytubefix.exceptions import RegexMatchError, InterpretationError
from pytubefix.jsinterp import JSInterpreter, extract_player_js_global_var

logger = logging.getLogger(__name__)

class Cipher:
    def __init__(self, js: str, js_url: str):
        self.js_url = js_url
        self.js = js
        self._sig_param_val = None
        self._nsig_param_val = None
        self.sig_function_name = self.get_sig_function_name(js, js_url)
        self.nsig_function_name = self.get_nsig_function_name(js, js_url)
        self.calculated_n = None
        self.js_interpreter = JSInterpreter(js)

    def get_nsig(self, n: str):
        """Transform the `n` parameter for throttling/signature."""
        try:
            # Python-only version: use JSInterpreter
            nsig = self.js_interpreter.call_function(self.nsig_function_name, n)
        except Exception as e:
            raise InterpretationError(js_url=self.js_url, reason=e)

        if not isinstance(nsig, str):
            raise InterpretationError(js_url=self.js_url, reason=nsig)
        return nsig

    def get_sig(self, ciphered_signature: str) -> str:
        """Compute the stream signature."""
        try:
            sig = self.js_interpreter.call_function(self.sig_function_name, ciphered_signature)
        except Exception as e:
            raise InterpretationError(js_url=self.js_url, reason=e)

        if not isinstance(sig, str):
            raise InterpretationError(js_url=self.js_url, reason=sig)
        return sig

    def get_sig_function_name(self, js: str, js_url: str) -> str:
        """Extract name of the signature function from base.js"""
        function_patterns = [
            r'(?P<sig>[a-zA-Z0-9_$]+)\s*=\s*function\(\s*(?P<arg>[a-zA-Z0-9_$]+)\s*\)\s*{\s*(?P=arg)\.split\(',
            r'\b(?P<sig>[a-zA-Z0-9_$]{2,})\s*=\s*function\(\s*a\s*\)\s*{\s*a\s*=\s*a\.split\(',
            r'\.sig\|\|(?P<sig>[a-zA-Z0-9$]+)\('
        ]
        logger.debug("Looking for signature cipher name")
        for pattern in function_patterns:
            regex = re.compile(pattern)
            match = regex.search(js)
            if match:
                sig = match.group('sig')
                logger.debug(f'Signature cipher function name: {sig}')
                return sig
        raise RegexMatchError(caller="get_sig_function_name", pattern=f"multiple in {js_url}")

    def get_nsig_function_name(self, js: str, js_url: str):
        """Extract name of the throttling function from base.js"""
        logger.debug("Looking for nsig name")
        try:
            pattern = r"var\s*[a-zA-Z0-9$_]{3}\s*=\s*\[(?P<funcname>[a-zA-Z0-9$_]{3})\]"
            func_name = re.search(pattern, js)
            if func_name:
                return func_name.group("funcname")
            # Fallback: use global JS object
            global_obj, varname, code = extract_player_js_global_var(js)
            if global_obj and varname and code:
                global_obj = JSInterpreter(js).interpret_expression(code, {}, 100)
                for k, v in enumerate(global_obj):
                    if v.endswith('_w8_'):
                        pattern = rf'(?P<funcname>[a-zA-Z0-9_$]+)\('
                        func_name = re.search(pattern, js)
                        if func_name:
                            self._nsig_param_val = self._extract_nsig_param_val(js, func_name.group("funcname"))
                            return func_name.group("funcname")
        except Exception as e:
            raise e
        raise RegexMatchError(caller="get_nsig_function_name", pattern=f"not found in {js_url}")

    @staticmethod
    def _extract_nsig_param_val(code: str, func_name: str) -> list:
        """Extract control parameter list from the signature function."""
        pattern = re.compile(
            rf'(?P<func>{re.escape(func_name)})\s*\(\s*(?P<arg1>[A-Za-z0-9_$]+)'
            r'(?:\s*,\s*(?P<arg2>[A-Za-z0-9_$]+))?.*?\)',
            re.MULTILINE
        )
        results = []
        for m in pattern.finditer(code):
            chosen = m.group('arg2') if m.group('arg2') else m.group('arg1')
            results.append(chosen)
        return results
