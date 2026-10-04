import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import credentials


class CredentialsTest(unittest.TestCase):
    def test_literals_precedence_and_no_environment_export(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / '.env'
            path.write_text('# comment\nexport LINEAR_API_KEY = "literal-$(never-execute)" # note\nUNRELATED=ignored\n')
            with patch.object(credentials, 'ENV_FILE', path), patch.dict(os.environ, {}, clear=True):
                self.assertEqual(credentials.credential('LINEAR_API_KEY'), 'literal-$(never-execute)')
                self.assertNotIn('LINEAR_API_KEY', os.environ)
                os.environ['LINEAR_API_KEY'] = 'environment-wins'
                self.assertEqual(credentials.credential('LINEAR_API_KEY'), 'environment-wins')

    def test_errors_do_not_expose_value(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / '.env'
            with patch.object(credentials, 'ENV_FILE', path), patch.dict(os.environ, {}, clear=True):
                self.assertIsNone(credentials.credential('LINEAR_API_KEY'))
                for raw in ('"secret-unclosed', 'secret value'):
                    path.write_text('LINEAR_API_KEY=' + raw)
                    with self.assertRaises(ValueError) as error:
                        credentials.credential('LINEAR_API_KEY')
                    self.assertNotIn('secret', str(error.exception))


if __name__ == '__main__':
    unittest.main()
